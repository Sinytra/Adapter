package org.sinytra.adapter.patch.transformer.dynfix;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.*;
import java.util.stream.Stream;

public class DynFixMethodComparison implements DynamicFixer<DynFixMethodComparison.Data> {
    public record Data(AbstractInsnNode cleanInjectionInsn) {
    }

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.INJECT)) {
            MethodContext.TargetPair cleanInjectionTarget = methodContext.findCleanInjectionTarget();
            List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanInjectionTarget);
            if (cleanInsns.size() == 1) {
                return new Data(cleanInsns.getFirst());
            }
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        List<List<AbstractInsnNode>> cleanLabels = getLabelsInMethod(methodContext.findCleanInjectionTarget().methodNode());
        List<List<AbstractInsnNode>> cleanLabelsOriginal = List.copyOf(cleanLabels);

        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();
        List<List<AbstractInsnNode>> cleanMatchedLabels = cleanLabels.stream()
            .filter(insns -> insns.contains(cleanInjectionInsn))
            .toList();
        if (cleanMatchedLabels.size() != 1) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> cleanLabel = cleanMatchedLabels.getFirst();

        List<List<AbstractInsnNode>> dirtyLabels = getLabelsInMethod(methodContext.findDirtyInjectionTarget().methodNode());
        List<List<AbstractInsnNode>> dirtyLabelsOriginal = List.copyOf(dirtyLabels);

        Map<List<AbstractInsnNode>, List<AbstractInsnNode>> matchedLabels = new LinkedHashMap<>();
        for (List<AbstractInsnNode> cleanInsns : cleanLabelsOriginal) {
            for (List<AbstractInsnNode> dirtyInsns : dirtyLabels) {
                if (InstructionMatcher.test(cleanInsns, dirtyInsns, InsnComparator.IGNORE_VAR_INDEX | InsnComparator.IGNORE_LINE_NUMBERS)) {
                    matchedLabels.put(cleanInsns, dirtyInsns);
                    cleanLabels.remove(cleanInsns);
                    dirtyLabels.remove(dirtyInsns);
                    break;
                }
            }
        }

        Pair<List<AbstractInsnNode>, List<AbstractInsnNode>> patchRange = findPatchHunkRange(cleanLabel, cleanLabelsOriginal, matchedLabels);
        if (patchRange == null) {
            return Patch.Result.PASS;
        }

        ClassNode cleanTargetClass = methodContext.findCleanInjectionTarget().classNode();
        List<List<AbstractInsnNode>> hunkLabels = dirtyLabelsOriginal.subList(dirtyLabelsOriginal.indexOf(patchRange.getFirst()) + 1, dirtyLabelsOriginal.indexOf(patchRange.getSecond()));
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
        return createInjectionPoint(targetClass, minsn, methodContext);
    }

    private static Patch.Result createInjectionPoint(ClassNode newTargetClass, MethodInsnNode minsn, MethodContext methodContext) {
        Type selfType = Type.getObjectType(methodContext.findDirtyInjectionTarget().classNode().name);
        Type[] params = Type.getArgumentTypes(minsn.desc);
        int selfIndex = Stream.of(Stream.iterate(0, i -> i < params.length, i -> i + 1)
                .filter(i -> params[i].equals(selfType))
                .toList())
            .filter(list -> list.size() == 1)
            .map(List::getFirst)
            .findFirst()
            .orElse(-1);
        if (selfIndex == -1) {
            return Patch.Result.PASS;
        }

        List<AbstractInsnNode> callInsns = MethodCallAnalyzer.findMethodCallParamInsns(methodContext.findDirtyInjectionTarget().methodNode(), minsn);
        if (callInsns == null || callInsns.size() <= selfIndex) {
            return Patch.Result.PASS;
        }
        AbstractInsnNode selfParamInsn = callInsns.get(selfIndex);
        if (selfParamInsn instanceof VarInsnNode varInsn && varInsn.getOpcode() == Opcodes.ALOAD && varInsn.var == 0) {
            // Cool, out instance is passed into the method. Now let's inject there and call the old mixin method
            ClassNode generatedTarget = methodContext.patchContext().environment().classGenerator().getOrGenerateMixinClass(methodContext.getMixinClass(), newTargetClass.name, null);
            methodContext.patchContext().environment().refmapHolder().copyEntries(methodContext.getMixinClass().name, generatedTarget.name);
            // Generate a method with the same injector annotation
            MethodNode originalMixinMethod = methodContext.getMixinMethod();
            String name = originalMixinMethod.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
            List<Type> originalParams = List.of(Type.getArgumentTypes(originalMixinMethod.desc));
            List<Type> newParams = ImmutableList.<Type>builder().add(Type.getArgumentTypes(minsn.desc)).add(AdapterUtil.CI_TYPE).build();
            // Make sure we have all required params
            if (!new HashSet<>(newParams).containsAll(originalParams)) {
                return Patch.Result.PASS;
            }

            String desc = Type.getMethodDescriptor(Type.VOID_TYPE, newParams.toArray(Type[]::new));
            // Change target
            BundledMethodTransform.builder().modifyTarget(minsn.name + minsn.desc).apply(methodContext);
            MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, desc, null, null);
            invokerMixinMethod.visibleAnnotations = new ArrayList<>(originalMixinMethod.visibleAnnotations);
            // Make original mixin a unique public method
            originalMixinMethod.access = OpcodeUtil.setAccessVisibility(originalMixinMethod.access, Opcodes.ACC_PUBLIC);
            originalMixinMethod.visibleAnnotations.remove(methodContext.methodAnnotation().unwrap());
            originalMixinMethod.visitAnnotation(MixinConstants.UNIQUE, true);
            // Now call the original mixin
            GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
            gen.newLabel();
            gen.loadArg(selfIndex);
            for (Type type : originalParams) {
                gen.loadArg(newParams.indexOf(type));
            }
            gen.invokeVirtual(selfType, new Method(originalMixinMethod.name, originalMixinMethod.desc));
            gen.newLabel();
            gen.returnValue();
            gen.newLabel();
            gen.endMethod();
            return Patch.Result.APPLY;
        }
        return Patch.Result.PASS;
    }

    // TODO This should be an automatic upgrade tbh
    private static void adjustInjectorOrdinalForNewMethod(MethodInsnNode minsn, MethodContext methodContext) {
        AnnotationValueHandle<Integer> handle = methodContext.injectionPointAnnotationOrThrow().<Integer>getValue("ordinal").orElse(null);
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

    @Nullable
    private static Pair<List<AbstractInsnNode>, List<AbstractInsnNode>> findPatchHunkRange(List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> cleanLabels, Map<List<AbstractInsnNode>, List<AbstractInsnNode>> matchedLabels) {
        // Find last matched dirty label BEFORE the injection point
        List<AbstractInsnNode> dirtyLabelBefore = Stream.iterate(cleanLabels.indexOf(cleanLabel), i -> i > 0, i -> i - 1)
            .map(i -> matchedLabels.get(cleanLabels.get(i)))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (dirtyLabelBefore == null) {
            return null;
        }

        // Find first matched dirty label AFTER the injection point
        List<AbstractInsnNode> dirtyLabelAfter = Stream.iterate(cleanLabels.indexOf(cleanLabel), i -> i < cleanLabels.size(), i -> i + 1)
            .map(i -> matchedLabels.get(cleanLabels.get(i)))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (dirtyLabelAfter == null) {
            return null;
        }

        return Pair.of(dirtyLabelBefore, dirtyLabelAfter);
    }

    private static List<List<AbstractInsnNode>> getLabelsInMethod(MethodNode methodNode) {
        List<List<AbstractInsnNode>> list = new ArrayList<>();
        List<AbstractInsnNode> workingList = null;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof FrameNode) {
                continue;
            }
            if (insn instanceof LabelNode) {
                if (workingList != null) {
                    list.add(workingList);
                }
                workingList = new ArrayList<>();
            }
            workingList.add(insn);
        }
        return list;
    }
}
