package org.sinytra.adapter.analysis.compare;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.flow.DataFlow;

import java.util.*;
import java.util.function.IntFunction;

public final class MethodAlignment {
    private static final int MAX_LCS_CELLS = 250000;

    public enum Confidence {
        EXACT,
        FINGERPRINT,
        POSITIONAL
    }

    public record Match(AbstractInsnNode dirty, Confidence confidence) {
    }

    public record Hunk(List<AbstractInsnNode> dirtyInsns, @Nullable AbstractInsnNode cleanBefore, @Nullable AbstractInsnNode cleanAfter) {
    }

    private record Fingerprint(String insn, List<String> producers) {
    }

    private record Pairing(int dirty, Confidence confidence, boolean inOrder) {
    }

    private final List<AbstractInsnNode> clean;
    private final List<AbstractInsnNode> dirty;
    private final Map<AbstractInsnNode, Integer> cleanIndices = new HashMap<>();
    private final Fingerprint[] cleanPrints;
    private final Fingerprint[] dirtyPrints;
    private final Map<Integer, Pairing> pairings = new HashMap<>();
    private final Map<Integer, Integer> reverse = new HashMap<>();

    private MethodAlignment(MethodNode cleanMethod, MethodNode dirtyMethod) {
        this.clean = realInsns(cleanMethod);
        this.dirty = realInsns(dirtyMethod);
        for (int i = 0; i < this.clean.size(); i++) {
            this.cleanIndices.put(this.clean.get(i), i);
        }
        this.cleanPrints = fingerprints(cleanMethod, this.clean);
        this.dirtyPrints = fingerprints(dirtyMethod, this.dirty);
    }

    public static MethodAlignment align(MethodNode cleanMethod, MethodNode dirtyMethod) {
        MethodAlignment alignment = new MethodAlignment(cleanMethod, dirtyMethod);
        alignment.alignInOrder();
        alignment.alignMoved();
        return alignment;
    }

    public double similarity() {
        int total = this.clean.size() + this.dirty.size();
        return total == 0 ? 1 : 2.0 * this.pairings.size() / total;
    }

    public List<Match> find(AbstractInsnNode cleanInsn) {
        Integer index = indexOf(cleanInsn);
        if (index == null) {
            return List.of();
        }
        Pairing pairing = this.pairings.get(index);
        if (pairing != null) {
            return List.of(new Match(this.dirty.get(pairing.dirty()), pairing.confidence()));
        }

        int[] range = dirtyRangeAround(index);
        List<Match> matches = new ArrayList<>();
        for (int i = range[0]; i < range[1]; i++) {
            if (!this.reverse.containsKey(i) && this.dirtyPrints[i].insn().equals(this.cleanPrints[index].insn())) {
                matches.add(new Match(this.dirty.get(i), Confidence.POSITIONAL));
            }
        }
        return matches;
    }

    public List<AbstractInsnNode> findDirtyAround(AbstractInsnNode cleanInsn) {
        Integer index = indexOf(cleanInsn);
        if (index == null || this.pairings.containsKey(index)) {
            return List.of();
        }
        int[] range = dirtyRangeAround(index);
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (int i = range[0]; i < range[1]; i++) {
            if (!this.reverse.containsKey(i)) {
                insns.add(this.dirty.get(i));
            }
        }
        return insns;
    }

    private int[] dirtyRangeAround(int index) {
        int from = 0;
        for (int i = index - 1; i >= 0; i--) {
            Pairing before = this.pairings.get(i);
            if (before != null && before.inOrder()) {
                from = before.dirty() + 1;
                break;
            }
        }
        int to = this.dirty.size();
        for (int i = index + 1; i < this.clean.size(); i++) {
            Pairing after = this.pairings.get(i);
            if (after != null && after.inOrder()) {
                to = after.dirty();
                break;
            }
        }
        return new int[]{from, to};
    }

    @Nullable
    public AbstractInsnNode resolve(AbstractInsnNode cleanInsn) {
        List<Match> matches = find(cleanInsn);
        return matches.size() == 1 && matches.getFirst().confidence() != Confidence.POSITIONAL ? matches.getFirst().dirty() : null;
    }

    public List<Hunk> findHunks() {
        List<Hunk> hunks = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= this.dirty.size(); i++) {
            boolean unmatched = i < this.dirty.size() && !this.reverse.containsKey(i);
            if (unmatched && start == -1) {
                start = i;
            } else if (!unmatched && start != -1) {
                hunks.add(new Hunk(List.copyOf(this.dirty.subList(start, i)), cleanPartner(start - 1), cleanPartner(i)));
                start = -1;
            }
        }
        return hunks;
    }

    @Nullable
    private AbstractInsnNode cleanPartner(int dirtyIndex) {
        Integer cleanIndex = dirtyIndex >= 0 && dirtyIndex < this.dirty.size() ? this.reverse.get(dirtyIndex) : null;
        return cleanIndex != null ? this.clean.get(cleanIndex) : null;
    }

    @Nullable
    private Integer indexOf(AbstractInsnNode insn) {
        AbstractInsnNode current = insn;
        while (current != null && isPseudo(current)) {
            current = current.getNext();
        }
        return current != null ? this.cleanIndices.get(current) : null;
    }

    private void alignInOrder() {
        diff(0, this.clean.size(), 0, this.dirty.size(), i -> this.cleanPrints[i], i -> this.dirtyPrints[i], Confidence.EXACT);

        List<Integer> matched = new ArrayList<>(this.pairings.keySet());
        Collections.sort(matched);
        int prevClean = -1;
        int prevDirty = -1;
        for (int cleanIndex : matched) {
            int dirtyIndex = this.pairings.get(cleanIndex).dirty();
            diff(prevClean + 1, cleanIndex, prevDirty + 1, dirtyIndex, i -> this.cleanPrints[i].insn(), i -> this.dirtyPrints[i].insn(), Confidence.FINGERPRINT);
            prevClean = cleanIndex;
            prevDirty = dirtyIndex;
        }
        diff(prevClean + 1, this.clean.size(), prevDirty + 1, this.dirty.size(), i -> this.cleanPrints[i].insn(), i -> this.dirtyPrints[i].insn(), Confidence.FINGERPRINT);
    }

    private void alignMoved() {
        Map<Fingerprint, List<Integer>> cleanUnmatched = new HashMap<>();
        for (int i = 0; i < this.clean.size(); i++) {
            if (!this.pairings.containsKey(i)) {
                cleanUnmatched.computeIfAbsent(this.cleanPrints[i], k -> new ArrayList<>()).add(i);
            }
        }
        Map<Fingerprint, List<Integer>> dirtyUnmatched = new HashMap<>();
        for (int i = 0; i < this.dirty.size(); i++) {
            if (!this.reverse.containsKey(i)) {
                dirtyUnmatched.computeIfAbsent(this.dirtyPrints[i], k -> new ArrayList<>()).add(i);
            }
        }
        cleanUnmatched.forEach((print, cleanIndices) -> {
            List<Integer> dirtyIndices = dirtyUnmatched.get(print);
            if (cleanIndices.size() == 1 && dirtyIndices != null && dirtyIndices.size() == 1) {
                pair(cleanIndices.getFirst(), dirtyIndices.getFirst(), Confidence.FINGERPRINT, false);
            }
        });
    }

    private void diff(int cleanFrom, int cleanTo, int dirtyFrom, int dirtyTo, IntFunction<Object> cleanKey, IntFunction<Object> dirtyKey, Confidence confidence) {
        while (cleanFrom < cleanTo && dirtyFrom < dirtyTo && cleanKey.apply(cleanFrom).equals(dirtyKey.apply(dirtyFrom))) {
            pair(cleanFrom++, dirtyFrom++, confidence, true);
        }
        while (cleanFrom < cleanTo && dirtyFrom < dirtyTo && cleanKey.apply(cleanTo - 1).equals(dirtyKey.apply(dirtyTo - 1))) {
            pair(--cleanTo, --dirtyTo, confidence, true);
        }
        if (cleanFrom >= cleanTo || dirtyFrom >= dirtyTo) {
            return;
        }

        Map<Object, int[]> counts = new HashMap<>();
        for (int i = cleanFrom; i < cleanTo; i++) {
            int[] entry = counts.computeIfAbsent(cleanKey.apply(i), k -> new int[]{0, 0, -1, -1});
            entry[0]++;
            entry[2] = i;
        }
        for (int i = dirtyFrom; i < dirtyTo; i++) {
            int[] entry = counts.get(dirtyKey.apply(i));
            if (entry != null) {
                entry[1]++;
                entry[3] = i;
            }
        }
        List<int[]> unique = new ArrayList<>();
        for (int[] entry : counts.values()) {
            if (entry[0] == 1 && entry[1] == 1) {
                unique.add(new int[]{entry[2], entry[3]});
            }
        }
        if (unique.isEmpty()) {
            lcs(cleanFrom, cleanTo, dirtyFrom, dirtyTo, cleanKey, dirtyKey, confidence);
            return;
        }
        unique.sort(Comparator.comparingInt(pair -> pair[0]));
        List<int[]> anchors = longestIncreasing(unique);

        int prevClean = cleanFrom - 1;
        int prevDirty = dirtyFrom - 1;
        for (int[] anchor : anchors) {
            diff(prevClean + 1, anchor[0], prevDirty + 1, anchor[1], cleanKey, dirtyKey, confidence);
            pair(anchor[0], anchor[1], confidence, true);
            prevClean = anchor[0];
            prevDirty = anchor[1];
        }
        diff(prevClean + 1, cleanTo, prevDirty + 1, dirtyTo, cleanKey, dirtyKey, confidence);
    }

    private void lcs(int cleanFrom, int cleanTo, int dirtyFrom, int dirtyTo, IntFunction<Object> cleanKey, IntFunction<Object> dirtyKey, Confidence confidence) {
        int n = cleanTo - cleanFrom;
        int m = dirtyTo - dirtyFrom;
        if ((long) n * m > MAX_LCS_CELLS) {
            return;
        }
        int[][] table = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                table[i][j] = cleanKey.apply(cleanFrom + i).equals(dirtyKey.apply(dirtyFrom + j))
                    ? table[i + 1][j + 1] + 1
                    : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (cleanKey.apply(cleanFrom + i).equals(dirtyKey.apply(dirtyFrom + j))) {
                pair(cleanFrom + i++, dirtyFrom + j++, confidence, true);
            } else if (table[i + 1][j] >= table[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
    }

    private static List<int[]> longestIncreasing(List<int[]> pairs) {
        int[] tails = new int[pairs.size()];
        int[] previous = new int[pairs.size()];
        int length = 0;
        for (int i = 0; i < pairs.size(); i++) {
            int value = pairs.get(i)[1];
            int lo = 0;
            int hi = length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (pairs.get(tails[mid])[1] < value) lo = mid + 1;
                else hi = mid;
            }
            previous[i] = lo > 0 ? tails[lo - 1] : -1;
            tails[lo] = i;
            if (lo == length) length++;
        }
        List<int[]> result = new ArrayList<>();
        for (int i = length > 0 ? tails[length - 1] : -1; i != -1; i = previous[i]) {
            result.add(pairs.get(i));
        }
        return result.reversed();
    }

    private void pair(int cleanIndex, int dirtyIndex, Confidence confidence, boolean inOrder) {
        if (this.pairings.containsKey(cleanIndex) || this.reverse.containsKey(dirtyIndex)) {
            return;
        }
        Confidence actual = confidence == Confidence.FINGERPRINT && inOrder && this.cleanPrints[cleanIndex].equals(this.dirtyPrints[dirtyIndex])
            ? Confidence.EXACT : confidence;
        this.pairings.put(cleanIndex, new Pairing(dirtyIndex, actual, inOrder));
        this.reverse.put(dirtyIndex, cleanIndex);
    }

    private static List<AbstractInsnNode> realInsns(MethodNode method) {
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (!isPseudo(insn)) {
                insns.add(insn);
            }
        }
        return insns;
    }

    private static boolean isPseudo(AbstractInsnNode insn) {
        return insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode;
    }

    private static Fingerprint[] fingerprints(MethodNode method, List<AbstractInsnNode> insns) {
        DataFlow flow = DataFlow.of(method);
        Fingerprint[] prints = new Fingerprint[insns.size()];
        boolean analyzable = true;
        for (int i = 0; i < insns.size(); i++) {
            AbstractInsnNode insn = insns.get(i);
            List<String> producers = List.of();
            if (analyzable) {
                try {
                    producers = flow.operands(insn).stream().map(MethodAlignment::describeProducers).toList();
                } catch (RuntimeException e) {
                    analyzable = false;
                }
            }
            prints[i] = new Fingerprint(describe(insn), producers);
        }
        return prints;
    }

    private static String describeProducers(SourceValue value) {
        return value.insns.size() == 1 ? describe(value.insns.iterator().next()) : "*";
    }

    private static String describe(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return switch (insn) {
            case MethodInsnNode m -> op + " " + m.owner + "." + m.name + m.desc;
            case FieldInsnNode f -> op + " " + f.owner + "." + f.name + ":" + f.desc;
            case TypeInsnNode t -> op + " " + t.desc;
            case LdcInsnNode l -> "LDC " + l.cst.getClass().getSimpleName() + " " + l.cst;
            case IntInsnNode n -> op + " " + n.operand;
            case IincInsnNode n -> "IINC " + n.incr;
            case MultiANewArrayInsnNode n -> op + " " + n.desc + " " + n.dims;
            case TableSwitchInsnNode n -> op + " " + n.min + ".." + n.max;
            case LookupSwitchInsnNode n -> op + " " + n.keys;
            case InvokeDynamicInsnNode n -> op + " " + n.name + n.desc + " " + n.bsm.getName() + " " + Arrays.stream(n.bsmArgs)
                .map(arg -> arg instanceof Handle handle ? handle.getDesc() : String.valueOf(arg))
                .toList();
            default -> String.valueOf(op);
        };
    }
}
