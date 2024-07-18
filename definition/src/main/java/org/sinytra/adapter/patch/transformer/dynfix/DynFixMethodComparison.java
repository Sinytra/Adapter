package org.sinytra.adapter.patch.transformer.dynfix;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.MethodLabelComparator;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;
import org.sinytra.adapter.patch.transformer.MirrorableExtractMixin;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.transformer.param.*;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DynFixMethodComparison implements DynamicFixer<DynFixMethodComparison.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.WRAP_OPERATION);

    public record Data(AbstractInsnNode cleanInjectionInsn) {}

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS) && methodContext.findDirtyInjectionTarget() != null) {
            MethodContext.TargetPair cleanInjectionTarget = methodContext.findCleanInjectionTarget();
            if (cleanInjectionTarget == null) {
                return null;
            }
            List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanInjectionTarget);
            if (!cleanInsns.isEmpty()) {
                return new Data(cleanInsns.getFirst());
            }
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();
        MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInjectionInsn, methodContext);
        if (comparisonResult == null) {
            return Patch.Result.PASS;
        }
        List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return handleWrapOperationToInstanceOf(cleanInjectionInsn, comparisonResult.cleanLabel(), hunkLabels, methodContext)
                .orElseGet(() -> handleTargetModification(hunkLabels, methodContext));
        }

        ClassNode cleanTargetClass = methodContext.findCleanInjectionTarget().classNode();
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn && minsn.getOpcode() == Opcodes.INVOKESTATIC && !minsn.owner.equals(cleanTargetClass.name)) {
                    Patch.Result result = attemptExtractMixin(minsn, methodContext);
                    if (result != Patch.Result.PASS) {
                        return result;
                    }
                }
            }
        }

        // If extraction fails, fall back to injecting at the nearest method call in dirty code
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn) {
                    String newInjectionPoint = Type.getObjectType(minsn.owner).getDescriptor() + minsn.name + minsn.desc;
                    return new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(methodContext);
                }
            }
        }

        return Patch.Result.PASS;
    }

    private static Patch.Result handleWrapOperationToInstanceOf(AbstractInsnNode cleanInjectionInsn, List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        if (!(cleanInjectionInsn instanceof MethodInsnNode minsn) || hunkLabels.size() != 1 || !(cleanLabel.getLast() instanceof JumpInsnNode) || cleanLabel.stream().anyMatch(i -> i instanceof TypeInsnNode)) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> dirtyLabel = hunkLabels.getFirst();
        if (!(dirtyLabel.getLast() instanceof JumpInsnNode)) {
            return Patch.Result.PASS;
        }
        List<TypeInsnNode> instanceOfCalls = dirtyLabel.stream().filter(i -> i instanceof TypeInsnNode).map(i -> (TypeInsnNode) i).toList();
        if (instanceOfCalls.size() != 1) {
            return Patch.Result.PASS;
        }
        TypeInsnNode instanceOfCall = instanceOfCalls.getFirst();
        MethodNode methodNode = methodContext.getMixinMethod();
        LocalVariableLookup mixinLocals = new LocalVariableLookup(methodNode);

        Type[] argsTypes = Type.getArgumentTypes(methodNode.desc);
        Set<Integer> paramVars = new HashSet<>();
        for (int i = 0; i < argsTypes.length; i++) {
            if (argsTypes[i].equals(AdapterUtil.OPERATION_TYPE)) {
                break;
            }
            LocalVariableNode lvn = mixinLocals.getByParameterOrdinal(i);
            paramVars.add(lvn.index);
        }

        ParamTransformationUtil.extractWrapOperation(methodContext, methodNode, List.of(argsTypes), op -> {
            for (int i = 1; i < paramVars.size(); i++) {
                op.removeParameter(i);
            }
        });

        List<AbstractInsnNode> originalOpCall = ParamTransformationUtil.findWrapOperationOriginalCallArgs(methodNode, methodContext);
        Multimap<Integer, VarInsnNode> usedVars = HashMultimap.create();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && !originalOpCall.contains(insn) && paramVars.contains(varInsn.var)) {
                usedVars.put(varInsn.var, varInsn);
            }
        }

        List<AbstractInsnNode> originalCallArgs = MethodCallAnalyzer.findMethodCallParamInsns(methodContext.findCleanInjectionTarget().methodNode(), minsn);
        LocalVariableNode instanceLocal = mixinLocals.getByParameterOrdinal(0);
        for (int paramVar : usedVars.keySet()) {
            if (paramVar == instanceLocal.index) {
                int cleanOrdinal = methodContext.cleanLocalsTable().getTypedOrdinal(methodContext.cleanLocalsTable().getByIndex(((VarInsnNode) originalCallArgs.getFirst()).var)).orElse(-1);
                if (cleanOrdinal == -1) {
                    return Patch.Result.PASS;
                }
                LocalVariableNode dirtyLocal = methodContext.dirtyLocalsTable().getByTypedOrdinal(Type.getType(instanceLocal.desc), cleanOrdinal).orElse(null);
                if (dirtyLocal == null) {
                    return Patch.Result.PASS;
                }
                TransformParameters.builder()
                    .transform(new InjectParameterTransform(argsTypes.length, Type.getType(dirtyLocal.desc), false))
                    .build()
                    .apply(methodContext);
                int newOrdinal = Type.getArgumentTypes(methodNode.desc).length - 1;
                AnnotationVisitor visitor = methodNode.visitParameterAnnotation(newOrdinal, MixinConstants.LOCAL, false);
                visitor.visit("ordinal", cleanOrdinal);
                visitor.visitEnd();
                int newIndex = new LocalVariableLookup(methodNode).getByParameterOrdinal(newOrdinal).index;
                usedVars.get(paramVar).forEach(varInsn -> varInsn.var = newIndex);
            } else {
                String loadedType = instanceOfCall.getPrevious() instanceof MethodInsnNode m ? Type.getReturnType(m.desc).getDescriptor() : null;
                LocalVariableNode node = mixinLocals.getByIndex(paramVar);
                if (loadedType == null || !loadedType.equals(node.desc)) {
                    return Patch.Result.PASS;
                }
                usedVars.get(paramVar).forEach(varInsn -> varInsn.var = instanceLocal.index);
            }
        }

        Patch.Result result = TransformParameters.builder()
            .transform(new ReplaceParametersTransformer(0, Type.getObjectType("java/lang/Object"), false))
            .chain(b -> {
                for (int i = 1; i < paramVars.size(); i++) {
                    b.transform(new RemoveParameterTransformer(i, false));
                }
            })
            .build()
            .apply(methodContext);
        if (result == Patch.Result.PASS) {
            return Patch.Result.PASS;
        }

        AnnotationHandle ann = methodContext.methodAnnotation(); 
        ann.removeValues("at");
        AnnotationVisitor visitor = ann.unwrap().visitAnnotation("constant", MixinConstants.CONSTANT);
        visitor.visit("classValue", Type.getObjectType(instanceOfCall.desc));
        visitor.visitEnd();

        return Patch.Result.APPLY;
    }

    private static Patch.Result handleTargetModification(List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        ClassNode dirtyTarget = methodContext.findDirtyInjectionTarget().classNode();
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(dirtyTarget.name)) {
                    MethodNode method = MethodCallAnalyzer.findMethodByUniqueName(dirtyTarget, minsn.name);
                    if (!methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(dirtyTarget, method)).isEmpty()) {
                        return BundledMethodTransform.builder().modifyTarget(minsn.name + minsn.desc).apply(methodContext);
                    }
                }
            }
        }
        return Patch.Result.PASS;
    }

    private static Patch.Result attemptExtractMixin(MethodInsnNode minsn, MethodContext methodContext) {
        // Looks like some code was moved into a static method outside this class
        // Attempt extracting mixin
        ClassNode targetClass = methodContext.patchContext().environment().dirtyClassLookup().getClass(minsn.owner).orElse(null);
        if (targetClass == null) {
            return Patch.Result.PASS;
        }
        MethodNode targetMethod = methodContext.patchContext().environment().dirtyClassLookup().findMethod(minsn.owner, minsn.name, minsn.desc).orElse(null);
        if (targetMethod == null) {
            return Patch.Result.PASS;
        }
        adjustInjectorOrdinalForNewMethod(minsn, methodContext);
        List<AbstractInsnNode> newTargetInsns = methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(targetClass, targetMethod));
        if (newTargetInsns.isEmpty()) {
            return Patch.Result.PASS;
        }
        Patch.Result res = BundledMethodTransform.builder()
            .extractMixin(minsn.owner)
            // Applied only if previous transform succeeds
            .modifyTarget(minsn.name + minsn.desc)
            .build(true)
            .apply(methodContext);
        if (res != Patch.Result.PASS) {
            return res;
        }
        // Extraction failed? Let's try something else
        return new MirrorableExtractMixin(targetClass.name, minsn).apply(methodContext);
    }

    // TODO This should be an automatic upgrade tbh
    private static void adjustInjectorOrdinalForNewMethod(MethodInsnNode minsn, MethodContext methodContext) {
        AnnotationValueHandle<Integer> handle = methodContext.injectionPointAnnotationOrThrow().<Integer>getValue("ordinal").orElse(null);
        if (handle == null) {
            return;
        }
        int originalOrdinal = handle.get();
        // Temporarily adjust ordinal to account for previous calls that have not been moved to the new class
        if (handle != null) {
            handle.set(-1);
            List<AbstractInsnNode> insns = methodContext.computeInjectionTargetInsns(methodContext.findDirtyInjectionTarget());
            handle.set(originalOrdinal);
            int newOrdinal = originalOrdinal;
            for (AbstractInsnNode insn : methodContext.findDirtyInjectionTarget().methodNode().instructions) {
                if (insn == minsn) {
                    break;
                }
                if (insns.contains(insn)) {
                    newOrdinal--;
                }
            }
            if (newOrdinal >= 0) {
                handle.set(newOrdinal);
            }
        }
    }
}
