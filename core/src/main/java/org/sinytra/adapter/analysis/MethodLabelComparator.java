package org.sinytra.adapter.analysis;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;

import java.util.*;
import java.util.stream.Stream;

public class MethodLabelComparator {
    public record ComparisonResult(List<List<AbstractInsnNode>> patchedLabels, List<AbstractInsnNode> cleanLabel) {
    }

    @Nullable
    public static ComparisonResult findPatchedLabels(AbstractInsnNode cleanInjectionInsn, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) return null;
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) return null;
        
        List<List<AbstractInsnNode>> cleanLabels = getLabelsInMethod(cleanTarget.methodNode());
        List<List<AbstractInsnNode>> cleanLabelsOriginal = List.copyOf(cleanLabels);

        List<List<AbstractInsnNode>> cleanMatchedLabels = cleanLabels.stream()
            .filter(insns -> insns.contains(cleanInjectionInsn))
            .toList();
        if (cleanMatchedLabels.size() != 1) {
            return null;
        }
        List<AbstractInsnNode> cleanLabel = cleanMatchedLabels.getFirst();

        List<List<AbstractInsnNode>> dirtyLabels = getLabelsInMethod(dirtyTarget.methodNode());
        List<List<AbstractInsnNode>> dirtyLabelsOriginal = List.copyOf(dirtyLabels);

        Map<List<AbstractInsnNode>, List<AbstractInsnNode>> matchedLabels = new LinkedHashMap<>();
        for (List<AbstractInsnNode> cleanInsns : cleanLabelsOriginal) {
            List<List<AbstractInsnNode>> candidates = new ArrayList<>();

            for (List<AbstractInsnNode> dirtyInsns : dirtyLabels) {
                if (InstructionMatcher.test(cleanInsns, dirtyInsns, InsnComparator.IGNORE_VAR_INDEX | InsnComparator.IGNORE_LINE_NUMBERS)) {
                    candidates.add(dirtyInsns);
                }
            }
            // TODO Try and come up with something better
            // This prevents messing up the order of labels
            // Without this countermeasure, it might happen that a label that was deleted will match a seemingly identical label somewhere else in the method, which is wrong
            // We disable any duplicated until we can properly handle such cases
            if (candidates.size() == 1) {
                List<AbstractInsnNode> dirtyInsns = candidates.getFirst();
                matchedLabels.put(cleanInsns, dirtyInsns);
                cleanLabels.remove(cleanInsns);
                dirtyLabels.remove(dirtyInsns);
            }
        }

        Pair<List<AbstractInsnNode>, List<AbstractInsnNode>> patchRange = findPatchHunkRange(cleanLabel, cleanLabelsOriginal, matchedLabels);
        if (patchRange == null) {
            return null;
        }

        List<List<AbstractInsnNode>> patchedLabels;
        int to = dirtyLabelsOriginal.indexOf(patchRange.getSecond());
        if (patchRange.getFirst() == null) {
            patchedLabels = dirtyLabelsOriginal.subList(0, to);
        } else {
            int from = dirtyLabelsOriginal.indexOf(patchRange.getFirst()) + 1;
            if (from < to) {
                patchedLabels = dirtyLabelsOriginal.subList(dirtyLabelsOriginal.indexOf(patchRange.getFirst()) + 1, to);
            } else {
                return null;
            }
        }
        return new ComparisonResult(patchedLabels, cleanLabel);
    }

    @Nullable
    private static Pair<@Nullable List<AbstractInsnNode>, List<AbstractInsnNode>> findPatchHunkRange(List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> cleanLabels, Map<List<AbstractInsnNode>, List<AbstractInsnNode>> matchedLabels) {
        // Find last matched dirty label BEFORE the injection point
        List<AbstractInsnNode> dirtyLabelBefore;
        int cleanLabelOrdinal = cleanLabels.indexOf(cleanLabel);
        if (cleanLabelOrdinal == 0) {
            dirtyLabelBefore = null;
        } else {
            dirtyLabelBefore = Stream.iterate(cleanLabelOrdinal, i -> i >= 0, i -> i - 1)
                .map(i -> matchedLabels.get(cleanLabels.get(i)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            if (dirtyLabelBefore == null) {
                return null;
            }
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
