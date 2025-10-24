package org.sinytra.adapter.patch.transformer.dynfix;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.MethodLabelComparator;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.MethodUpgrader;
import org.sinytra.adapter.patch.transformer.operation.CompoundMethodTransform;
import org.sinytra.adapter.patch.transformer.operation.param.*;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionPoint;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.*;
import java.util.stream.Stream;

public class DynFixMethodComparison implements DynamicFixer<DynFixMethodComparison.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.WRAP_OPERATION, MixinConstants.MODIFY_ARG);

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
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();
        MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInjectionInsn, methodContext);
        if (comparisonResult == null) {
            return null;
        }
        List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_ARG)) {
            return handleModifyArgInjectionPoint(cleanInjectionInsn, hunkLabels, methodContext);
        }

        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return handleWrapOperationToInstanceOf(cleanInjectionInsn, comparisonResult.cleanLabel(), hunkLabels, methodContext)
                .or(() -> handleWrapOperationAdaptedTarget(cleanInjectionInsn, hunkLabels, methodContext))
                .or(() -> handleWrapOperationNewInjectionPoint(cleanInjectionInsn, comparisonResult.cleanLabel(), hunkLabels, methodContext))
                .or(() -> handleTargetModification(hunkLabels, methodContext))
                .orElse(null);
        }

        ClassNode cleanTargetClass = methodContext.findCleanInjectionTarget().classNode();
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn && minsn.getOpcode() == Opcodes.INVOKESTATIC && !minsn.owner.equals(cleanTargetClass.name)) {
                    Patch.Result result = attemptExtractMixin(minsn, methodContext);
                    if (result != Patch.Result.PASS) {
                        return FixResult.of(result, PatchAuditTrail.Match.FULL);
                    }
                }
            }
        }

        // If extraction fails, fall back to injecting at the nearest method call in dirty code
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn) {
                    String newInjectionPoint = Type.getObjectType(minsn.owner).getDescriptor() + minsn.name + minsn.desc;
                    return FixResult.of(new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(methodContext), PatchAuditTrail.Match.PARTIAL);
                }
            }
        }

        return null;
    }

    // Handle ModifyArg when targetting INDY values
    private static FixResult handleModifyArgInjectionPoint(AbstractInsnNode cleanInjectionInsn, List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        if (!(cleanInjectionInsn instanceof MethodInsnNode minsn)) {
            return null;
        }

        Type desiredType = Type.getArgumentTypes(methodContext.getMixinMethod().desc)[0];

        int index = MethodCallAnalyzer.getArgIndex(minsn.desc, desiredType) + 1;
        if (index == 0) {
            return null;
        }
        List<AbstractInsnNode> cleanCallParamInsns = MethodCallAnalyzer.findMethodCallParamInsns(methodContext.findCleanInjectionTarget().methodNode(), minsn);
        if (cleanCallParamInsns.size() <= index) {
            return null;
        }

        AbstractInsnNode targetArgInsn = cleanCallParamInsns.get(index);
        if (!(targetArgInsn instanceof InvokeDynamicInsnNode cleanIndy)) {
            return null;
        }

        MethodContext.TargetPair cleanTarget = methodContext.findCleanInjectionTarget();
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        // Handle cases where the method has been split off
        if (cleanIndy.bsmArgs.length > 2 && cleanIndy.bsmArgs[1] instanceof Handle handle && handle.getOwner().equals(dirtyTarget.classNode().name)) {
            MethodNode cleanMethod = MethodCallAnalyzer.findMethodByUniqueName(cleanTarget.classNode(), handle.getName()).orElse(null);
            if (cleanMethod == null) {
                return null;
            }
            MethodNode dirtyMethod = MethodCallAnalyzer.findMethodByUniqueName(dirtyTarget.classNode(), handle.getName()).orElse(null);
            if (dirtyMethod == null) {
                return null;
            }
            if (MethodCallAnalyzer.isDirtyDeprecatedMethod(cleanMethod, dirtyMethod)) {
                List<MethodNode> invocations = MethodCallAnalyzer.collectMethodInvocations(dirtyTarget.classNode(), dirtyMethod);
                if (invocations != null) {
                    MethodNode last = invocations.getLast();
                    if (last.desc.equals(dirtyMethod.desc)) {
                        InvokeDynamicInsnNode clone = (InvokeDynamicInsnNode) cleanIndy.clone(Map.of());
                        clone.bsmArgs = Stream.of(clone.bsmArgs).toArray();
                        clone.bsmArgs[1] = new Handle(handle.getTag(), handle.getOwner(), last.name, handle.getDesc(), handle.isInterface());
                        cleanIndy = clone;
                    }
                }
            }
        }

        MethodNode dirtyMethod = methodContext.findDirtyInjectionTarget().methodNode();
        List<MethodInsnNode> matches = new ArrayList<>();
        for (List<AbstractInsnNode> label : hunkLabels) {
            for (AbstractInsnNode insn : label) {
                if (insn instanceof MethodInsnNode m && Stream.of(Type.getArgumentTypes(m.desc)).filter(desiredType::equals).count() == 1) {
                    int argIndex = MethodCallAnalyzer.getArgIndex(m.desc, desiredType) + 1;
                    if (argIndex == 0) {
                        continue;
                    }
                    List<AbstractInsnNode> list = MethodCallAnalyzer.findMethodCallParamInsns(dirtyMethod, m);
                    if (list.size() > argIndex && list.get(argIndex) instanceof InvokeDynamicInsnNode dirtyIndy && InsnComparator.insnEqual(cleanIndy, dirtyIndy)) {
                        matches.add(m);
                    }
                }
            }
        }

        if (matches.size() == 1) {
            MethodInsnNode m = matches.getFirst();
            String newInjectionPoint = Type.getObjectType(m.owner).getDescriptor() + m.name + m.desc;

            Patch.Result result = CompoundMethodTransform.builder(new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false))
                .onSuccess(() -> (cls, mtd, mtx, ctx) -> {
                    int ordinal = MethodCallAnalyzer.getMethodCallOrdinal(dirtyMethod, m);
                    if (ordinal == -1) {
                        throw new IllegalStateException("Ordinal not found?");
                    }
                    AnnotationHandle handle = mtx.injectionPointAnnotationOrThrow();
                    handle.setOrAppendNonNull("ordinal", ordinal);
                    return Patch.Result.APPLY;
                })
                .apply(methodContext);

            return FixResult.of(result, PatchAuditTrail.Match.FULL);
        }

        return null;
    }

    private static Optional<FixResult> handleWrapOperationAdaptedTarget(AbstractInsnNode cleanInjectionInsn, List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        if (!(cleanInjectionInsn instanceof MethodInsnNode minsn) || hunkLabels.size() != 1) {
            return Optional.empty();
        }

        List<MethodInsnNode> methodCalls = hunkLabels.getFirst().stream()
            .filter(i -> i instanceof MethodInsnNode)
            .map(i -> (MethodInsnNode) i)
            .toList();
        if (methodCalls.size() != 1) {
            return Optional.empty();
        }

        Patch.Result result = WrapOperationSurgeon.tryUpgrade(methodContext, minsn, methodCalls.getLast());

        return Optional.ofNullable(FixResult.of(result, PatchAuditTrail.Match.FULL));
    } 

    private static Optional<FixResult> handleWrapOperationNewInjectionPoint(AbstractInsnNode cleanInjectionInsn, List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        if (!(cleanInjectionInsn instanceof MethodInsnNode minsn) || hunkLabels.size() != 1) {
            return Optional.empty();
        }
        Type cleanReturnType = Type.getReturnType(minsn.desc);
        List<AbstractInsnNode> dirtyLabel = hunkLabels.getFirst();
        List<String> cleanMethodCalls = cleanLabel.stream().filter(i -> i instanceof MethodInsnNode m && m.owner.equals(minsn.owner) && Type.getReturnType(m.desc).equals(cleanReturnType)).map(i -> ((MethodInsnNode) i).name).toList();
        List<MethodInsnNode> methodCalls = dirtyLabel.stream()
            .filter(i -> i instanceof MethodInsnNode m && m.owner.equals(minsn.owner) && Type.getReturnType(m.desc).equals(cleanReturnType) && !cleanMethodCalls.contains(m.name))
            .map(i -> (MethodInsnNode) i)
            .toList();
        if (methodCalls.size() != 1) {
            return Optional.empty();
        }
        MethodInsnNode dirtyMinsn = methodCalls.getFirst();
        Patch.Result result = CompoundMethodTransform.builder(b -> b
                .modifyInjectionPoint("INVOKE", MethodCallAnalyzer.getCallQualifier(dirtyMinsn)))
            .apply(methodContext);
        return Optional.of(FixResult.of(result, PatchAuditTrail.Match.FULL));
    }

    private static Optional<FixResult> handleWrapOperationToInstanceOf(AbstractInsnNode cleanInjectionInsn, List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        if (!(cleanInjectionInsn instanceof MethodInsnNode minsn) || hunkLabels.size() != 1 || !(cleanLabel.getLast() instanceof JumpInsnNode) || cleanLabel.stream().anyMatch(i -> i instanceof TypeInsnNode)) {
            return Optional.empty();
        }
        List<AbstractInsnNode> dirtyLabel = hunkLabels.getFirst();
        if (!(dirtyLabel.getLast() instanceof JumpInsnNode)) {
            return Optional.empty();
        }
        List<TypeInsnNode> instanceOfCalls = dirtyLabel.stream().filter(i -> i instanceof TypeInsnNode).map(i -> (TypeInsnNode) i).toList();
        if (instanceOfCalls.size() != 1) {
            return Optional.empty();
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
                    return Optional.empty();
                }
                LocalVariableNode dirtyLocal = methodContext.dirtyLocalsTable().getByTypedOrdinal(Type.getType(instanceLocal.desc), cleanOrdinal).orElse(null);
                if (dirtyLocal == null) {
                    return Optional.empty();
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
                    return Optional.empty();
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
            return Optional.empty();
        }

        AnnotationHandle ann = methodContext.methodAnnotation();
        ann.removeValues("at");
        AnnotationVisitor visitor = ann.unwrap().visitAnnotation("constant", MixinConstants.CONSTANT);
        visitor.visit("classValue", Type.getObjectType(instanceOfCall.desc));
        visitor.visitEnd();

        return Optional.of(FixResult.of(Patch.Result.APPLY, PatchAuditTrail.Match.FULL));
    }

    private static Optional<FixResult> handleTargetModification(List<List<AbstractInsnNode>> hunkLabels, MethodContext methodContext) {
        ClassNode dirtyTarget = methodContext.findDirtyInjectionTarget().classNode();
        for (List<AbstractInsnNode> insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(dirtyTarget.name)) {
                    MethodNode method = MethodCallAnalyzer.findMethodByUniqueName(dirtyTarget, minsn.name).orElse(null);
                    if (method != null && !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(dirtyTarget, method)).isEmpty()) {
                        Patch.Result result = CompoundMethodTransform.builder(b -> b.modifyTarget(minsn.name + minsn.desc))
                            .apply(methodContext);
                        return Optional.of(FixResult.of(result, PatchAuditTrail.Match.FULL));
                    }
                }
            }
        }
        return Optional.empty();
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

        MethodUpgrader.adjustInjectorOrdinalForNewMethod(minsn, methodContext);
        List<AbstractInsnNode> newTargetInsns = methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(targetClass, targetMethod));
        if (newTargetInsns.isEmpty()) {
            return Patch.Result.PASS;
        }

        return CompoundMethodTransform.builder(b -> b.extractMixin(minsn.owner))
            .onSuccess(b -> b.modifyTarget(minsn.name + minsn.desc))
            // Extraction failed? Let's try something else
            .onFail(() -> new MirrorableExtractMixin(targetClass.name, minsn))
            .apply(methodContext);
    }
}
