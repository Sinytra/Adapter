package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ExtractMixin;
import org.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import org.sinytra.adapter.patch.transformer.ModifyInjectionTarget;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DynFixMethodComparison implements DynamicFixer<DynFixMethodComparison.Data> {
    public record Data(AbstractInsnNode cleanInjectionInsn) {}

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
        List<InsnList> cleanLabels = getLabelsInMethod(methodContext.findCleanInjectionTarget().methodNode());
        List<InsnList> cleanLabelsOriginal = List.copyOf(cleanLabels);

        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();
        List<InsnList> cleanMatchedLabels = cleanLabels.stream()
            .filter(insns -> StreamSupport.stream(insns.spliterator(), false)
                .anyMatch(insn -> InsnComparator.instructionsEqual(cleanInjectionInsn, insn)))
            .toList();
        if (cleanMatchedLabels.size() != 1) {
            return Patch.Result.PASS;
        }
        InsnList cleanLabel = cleanMatchedLabels.getFirst();

        List<InsnList> dirtyLabels = getLabelsInMethod(methodContext.findDirtyInjectionTarget().methodNode());
        List<InsnList> dirtyLabelsOriginal = List.copyOf(dirtyLabels);

        Map<InsnList, InsnList> matchedLabels = new LinkedHashMap<>();
        for (InsnList cleanInsns : cleanLabelsOriginal) {
            for (InsnList dirtyInsns : dirtyLabels) {
                if (InstructionMatcher.test(cleanInsns, dirtyInsns, InsnComparator.IGNORE_VAR_INDEX | InsnComparator.IGNORE_LINE_NUMBERS)) {
                    matchedLabels.put(cleanInsns, dirtyInsns);
                    cleanLabels.remove(cleanInsns);
                    dirtyLabels.remove(dirtyInsns);
                    break;
                }
            }
        }

        Pair<InsnList, InsnList> patchRange = findPatchHunkRange(cleanLabel, cleanLabelsOriginal, matchedLabels);
        if (patchRange == null) {
            return Patch.Result.PASS;
        }

        ClassNode cleanTargetClass = methodContext.findCleanInjectionTarget().classNode();
        List<InsnList> hunkLabels = dirtyLabelsOriginal.subList(dirtyLabelsOriginal.indexOf(patchRange.getFirst()) + 1, dirtyLabelsOriginal.indexOf(patchRange.getSecond()));
        for (InsnList insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn && minsn.getOpcode() == Opcodes.INVOKESTATIC && !minsn.owner.equals(cleanTargetClass.name)) {
                    // Looks like some code was moved into a static method outside this class
                    // Attempt extracting mixin
                    // TODO Create universal transform builder class so that we don't need to reference transformer classes directly
                    ClassNode targetClass = methodContext.patchContext().environment().dirtyClassLookup().getClass(minsn.owner).orElse(null);
                    if (targetClass != null) {
                        MethodNode targetMethod = methodContext.patchContext().environment().dirtyClassLookup().findMethod(minsn.owner, minsn.name, minsn.desc).orElse(null);
                        if (targetMethod != null && !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(targetClass, targetMethod)).isEmpty()) {
                            Patch.Result extractResult = new ExtractMixin(minsn.owner).apply(classNode, methodNode, methodContext);
                            if (extractResult != Patch.Result.PASS) {
                                return extractResult.or(new ModifyInjectionTarget(List.of(minsn.name + minsn.desc), ModifyInjectionTarget.Action.OVERWRITE).apply(classNode, methodNode, methodContext));
                            }
                        }
                    }
                }
            }
        }

        // If extraction fails, fall back to injecting at the nearest method call in dirty code
        for (InsnList insns : hunkLabels) {
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode minsn) {
                    String newInjectionPoint = Type.getObjectType(minsn.owner).getDescriptor() + minsn.name + minsn.desc;
                    return new ModifyInjectionPoint("INVOKE", newInjectionPoint, true, false).apply(classNode, methodNode, methodContext);
                }
            }
        }

        return Patch.Result.PASS;
    }

    @Nullable
    private static Pair<InsnList, InsnList> findPatchHunkRange(InsnList cleanLabel, List<InsnList> cleanLabels, Map<InsnList, InsnList> matchedLabels) {
        // Find last matched dirty label BEFORE the injection point
        InsnList dirtyLabelBefore = Stream.iterate(cleanLabels.indexOf(cleanLabel), i -> i > 0, i -> i - 1)
            .map(i -> matchedLabels.get(cleanLabels.get(i)))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (dirtyLabelBefore == null) {
            return null;
        }

        // Find first matched dirty label AFTER the injection point
        InsnList dirtyLabelAfter = Stream.iterate(cleanLabels.indexOf(cleanLabel), i -> i < cleanLabels.size(), i -> i + 1)
            .map(i -> matchedLabels.get(cleanLabels.get(i)))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        if (dirtyLabelAfter == null) {
            return null;
        }

        return Pair.of(dirtyLabelBefore, dirtyLabelAfter);
    }

    private static List<InsnList> getLabelsInMethod(MethodNode methodNode) {
        Map<LabelNode, LabelNode> labels = StreamSupport.stream(methodNode.instructions.spliterator(), false)
            .map(a -> a instanceof LabelNode label ? label : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Function.identity(), l -> new LabelNode()));
        List<InsnList> list = new ArrayList<>();
        InsnList workingList = null;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof FrameNode) {
                continue;
            }
            if (insn instanceof LabelNode) {
                if (workingList != null) {
                    list.add(workingList);
                }
                workingList = new InsnList();
            }
            workingList.add(insn.clone(labels));
        }
        return list;
    }
}
