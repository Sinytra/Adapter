package dev.su5ed.sinytra.adapter.patch.analysis;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.util.*;

public class EnhancedParamsDiff {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = Boolean.getBoolean("adapter.definition.paramdiff.debug");

    public static ParametersDiff create(List<Type> clean, List<Type> dirty) {
        ParamDiffBuilder builder = new ParamDiffBuilder();
        List<TypeWithContext> cleanQueue = createPositionedList(clean);
        List<TypeWithContext> dirtyQueue = createPositionedList(dirty);
        if (DEBUG) {
            LOGGER.info("Comparing types:\n{}", printTable(cleanQueue, dirtyQueue));
        }

        while (!cleanQueue.isEmpty()) {
            // Look ahead for matching types at the beginning of the list
            // If the first two are equal, remove the first ones and repeat
            if (cleanQueue.size() > 1 && dirtyQueue.size() > 1 && cleanQueue.get(0).sameType(dirtyQueue.get(0)) && cleanQueue.get(1).sameType(dirtyQueue.get(1))) {
                cleanQueue.remove(0);
                dirtyQueue.remove(0);
                continue;
            }
            // Handle replaced types, needs improving
            if (replaceType(builder, 0, cleanQueue, dirtyQueue) || cleanQueue.size() == 2 && dirtyQueue.size() == 2 && replaceType(builder, 1, cleanQueue, dirtyQueue)) {
                continue;
            }
            // Check for swapped / reordered types. Also handles unique-type insertions
            SwapResult swapResult = checkForSwaps(builder, cleanQueue, dirtyQueue);
            if (swapResult != null) {
                dirtyQueue.removeAll(swapResult.removeDirty);
                builder.merge(swapResult.parametersDiff);
                cleanQueue.clear();
                dirtyQueue.clear();
                break;
            }
            // Find the smallest set of matching types by locating the position of the next clean type in the dirty list
            int dirtyTypeIndex = cleanQueue.size() == 1 ? dirty.size() - 1 : lookAhead(dirtyQueue, cleanQueue.get(1));
            if (dirtyTypeIndex != -1) {
                int offset = cleanQueue.get(0).sameType(dirtyQueue.get(0)) ? 1 : 2;
                List<TypeWithContext> compareClean = extract(cleanQueue, 2);
                List<TypeWithContext> compareDirty = extract(dirtyQueue, dirtyTypeIndex + offset);

                compare(builder, compareClean, compareDirty);
            }
            // If there is no match for the first available type, look further ahead. This is good for handling cases
            // where the sizes of both sets are equal and the first couple types have been replaced / modified
            else if (cleanQueue.size() == dirtyQueue.size() && (dirtyTypeIndex = findClosestMatch(cleanQueue, dirtyQueue)) > 1) {
                List<TypeWithContext> compareClean = extract(cleanQueue, dirtyTypeIndex);
                List<TypeWithContext> compareDirty = extract(dirtyQueue, dirtyTypeIndex);

                compare(builder, compareClean, compareDirty);
            } else if (cleanQueue.size() > 1) {
                TypeWithContext type = cleanQueue.remove(1);
                if (DEBUG) {
                    LOGGER.info("Removing type {}", type);
                }
                builder.remove(type.pos());
            } else {
                throw new IllegalStateException("undefined behavior");
            }
        }
        if (!dirtyQueue.isEmpty()) {
            for (TypeWithContext type : dirtyQueue) {
                builder.insert(type.pos(), type.type());
            }
        }

        return builder.build(clean.size());
    }

    private static int findClosestMatch(List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue) {
        for (TypeWithContext typeWithContext : cleanQueue) {
            int pos = lookAhead(dirtyQueue, typeWithContext);
            if (pos != -1) {
                return pos;
            }
        }
        return -1;
    }

    private static boolean replaceType(ParamDiffBuilder builder, int index, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue) {
        if (index >= cleanQueue.size() || index >= dirtyQueue.size()) {
            return false;
        }
        TypeWithContext cleanType = cleanQueue.get(index);
        TypeWithContext dirtyType = dirtyQueue.get(index);
        int next = index + 1;
        // Handle possible insertions
        if (cleanQueue.size() != dirtyQueue.size() && dirtyQueue.size() > next && cleanType.sameType(dirtyQueue.get(next)) || isPossiblyInjected(next, cleanQueue, dirtyQueue)) {
            return false;
        }
        if (!cleanType.sameType(dirtyType)) {
            if (DEBUG) {
                LOGGER.info("Replacing {} with {}", cleanType, dirtyType);
            }
            cleanQueue.remove(index);
            dirtyQueue.remove(index);
            builder.replace(dirtyType.pos(), dirtyType.type());
            return true;
        }
        return false;
    }

    private static boolean isPossiblyInjected(int index, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue) {
        for (int i = index; i < cleanQueue.size(); i++) {
            if (!cleanQueue.get(i).equals(dirtyQueue.get(i))) {
                return true;
            }
        }
        return false;
    }

    private record SwapResult(List<TypeWithContext> removeDirty, ParametersDiff parametersDiff) {
    }

    @Nullable
    private static SwapResult checkForSwaps(ParamDiffBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        Map<Type, Integer> cleanGroup = groupTypes(clean);
        Map<Type, Integer> dirtyGroup = groupTypes(dirty);
        MapDifference<Type, Integer> diff = Maps.difference(cleanGroup, dirtyGroup);

        List<TypeWithContext> rearrangeClean = new ArrayList<>(clean);
        List<TypeWithContext> rearrangeDirty = new ArrayList<>(dirty);
        List<TypeWithContext> removeDirty = new ArrayList<>();
        ParamDiffBuilder tempDiff = new ParamDiffBuilder();
        // Remove inserted parameters
        if (diff.entriesOnlyOnLeft().isEmpty() && !diff.entriesOnlyOnRight().isEmpty()) {
            for (Map.Entry<Type, Integer> entry : diff.entriesOnlyOnRight().entrySet()) {
                Type type = entry.getKey();
                Integer count = entry.getValue();
                if (count == 1) {
                    TypeWithContext inserted = dirty.stream().filter(t -> t.type().equals(type)).findFirst().orElseThrow();
                    dirtyGroup.remove(type);
                    rearrangeDirty.remove(inserted);
                    removeDirty.add(inserted);
                    int offset = inserted.pos() + (int) builder.getRemoved().stream().filter(i -> i < inserted.pos()).count();
                    tempDiff.insert(offset, inserted.type());
                }
            }
        } else if (!diff.entriesOnlyOnLeft().isEmpty() && diff.entriesOnlyOnRight().isEmpty()) {
            for (Map.Entry<Type, Integer> entry : diff.entriesOnlyOnLeft().entrySet()) {
                Type type = entry.getKey();
                Integer count = entry.getValue();
                if (count == 1) {
                    TypeWithContext inserted = clean.stream().filter(t -> t.type().equals(type)).findFirst().orElseThrow();
                    tempDiff.remove(clean.indexOf(inserted));
                    cleanGroup.remove(type);
                    rearrangeClean.remove(inserted);
                }
            }
        }

        if (!sameTypeCount(cleanGroup, dirtyGroup)) {
            return null;
        }
        if (DEBUG) {
            LOGGER.info("Checking for swaps in parameters:\n{}", printTable(rearrangeClean, rearrangeDirty));
        }

        // Remove leading equal types
        while (!rearrangeClean.isEmpty() && rearrangeClean.get(0).sameType(rearrangeDirty.get(0))) {
            rearrangeClean.remove(0);
            rearrangeDirty.remove(0);
        }
        // Remove trailing equal types
        while (!rearrangeClean.isEmpty() && rearrangeClean.get(rearrangeClean.size() - 1).sameType(rearrangeDirty.get(rearrangeDirty.size() - 1))) {
            rearrangeClean.remove(rearrangeClean.size() - 1);
            rearrangeDirty.remove(rearrangeDirty.size() - 1);
        }
        if (!rearrangeClean.isEmpty() && !rearrangeDirty.isEmpty()) {
            // Run rearrangement
            rearrange(builder, rearrangeClean, rearrangeDirty);
            return new SwapResult(removeDirty, tempDiff.build());
        } else if (rearrangeClean.isEmpty() && rearrangeDirty.isEmpty()) {
            return new SwapResult(removeDirty, tempDiff.build());
        }
        return null;
    }

    private static void rearrange(ParamDiffBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        record Rearrangement(TypeWithContext cleanType, TypeWithContext dirtyType, int fromRelative, int toRelative) {
        }

        Map<Type, Integer> cleanGroup = groupTypes(clean);
        Map<Type, Integer> dirtyGroup = groupTypes(dirty);
        if (!sameTypeCount(cleanGroup, dirtyGroup)) {
            return;
        }
        if (DEBUG) {
            LOGGER.info("Rearranging parameters:\n{}", printTable(clean, dirty));
        }

        List<Rearrangement> rearrangements = new ArrayList<>();
        for (TypeWithContext cleanType : clean) {
            if (cleanGroup.get(cleanType.type()) == 1 && dirtyGroup.get(cleanType.type()) == 1) {
                // Determine new type pos
                TypeWithContext dirtyType = dirty.stream().filter(cleanType::sameType).findFirst().orElseThrow();
                rearrangements.add(new Rearrangement(cleanType, dirtyType, clean.indexOf(cleanType), dirty.indexOf(dirtyType)));
            }
        }
        rearrangements.sort(Comparator.comparingInt(Rearrangement::toRelative));

        List<TypeWithContext> rearrangeClean = new ArrayList<>(clean);
        List<TypeWithContext> rearrangeDirty = new ArrayList<>(dirty);
        for (Rearrangement rearrangement : rearrangements) {
            boolean same = true;
            for (int i = 0; i < rearrangeClean.size(); i++) {
                if (!rearrangeClean.get(i).sameType(rearrangeDirty.get(i))) {
                    same = false;
                }
            }
            if (same) {
                break;
            }
            if (DEBUG) {
                LOGGER.info("Moving param {} to index {}", rearrangement.cleanType(), rearrangement.dirtyType().pos());
            }
            builder.move(rearrangement.cleanType().pos(), rearrangement.dirtyType().pos());
            rearrangeClean.add(rearrangement.toRelative(), rearrangeClean.remove(rearrangement.fromRelative()));
        }
    }

    private static void compare(ParamDiffBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        if (DEBUG) {
            LOGGER.info("Running comparison for:\n{}", printTable(clean, dirty));
        }

        Type[] cleanTypes = clean.stream().map(TypeWithContext::type).toArray(Type[]::new);
        Type[] dirtyTypes = dirty.stream().map(TypeWithContext::type).toArray(Type[]::new);
        ParametersDiff diff = ParametersDiff.compareTypeParameters(cleanTypes, dirtyTypes);
        if (DEBUG) {
            LOGGER.info("Comparison results:\n\tInserted: {}\n\tReplaced: {}\n\tSwapped:  {}\n\tRemoved:  {}", diff.insertions(), diff.removals(), diff.swaps(), diff.removals());
        }
        int indexOffset = !dirty.isEmpty() ? dirty.get(0).pos() : 0;
        builder.merge(diff, indexOffset);
    }

    private static List<TypeWithContext> createPositionedList(List<Type> list) {
        List<TypeWithContext> ret = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ret.add(new TypeWithContext(list.get(i), i));
        }
        return ret;
    }

    private static boolean sameTypeCount(Map<Type, Integer> cleanGroup, Map<Type, Integer> dirtyGroup) {
        if (cleanGroup.size() != dirtyGroup.size()) {
            return false;
        }
        for (Map.Entry<Type, Integer> entry : cleanGroup.entrySet()) {
            Integer dirtyCount = dirtyGroup.get(entry.getKey());
            if (dirtyCount == null || dirtyCount.intValue() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static <T> List<T> extract(List<T> list, int amount) {
        List<T> res = new ArrayList<>();
        for (int i = 0; i < amount && !list.isEmpty(); i++) {
            res.add(list.remove(0));
        }
        return res;
    }

    private static int lookAhead(List<TypeWithContext> types, TypeWithContext target) {
        for (int i = 0; i < types.size(); i++) {
            if (target.sameType(types.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static Map<Type, Integer> groupTypes(List<TypeWithContext> list) {
        Map<Type, Integer> map = new HashMap<>();
        for (TypeWithContext type : list) {
            map.compute(type.type(), (t, old) -> old == null ? 1 : old + 1);
        }
        return map;
    }

    private static String printTable(List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        StringBuilder builder = new StringBuilder();
        builder.append("\t| %5s | %25s%s%26s | %25s%s%26s |\n".formatted("Index", "", "Clean", "", "", "Dirty", ""));
        builder.append("\t");
        builder.append("=".repeat(127));
        builder.append("\n");
        int max = Math.max(clean.size(), dirty.size());
        for (int i = 0; i < max; i++) {
            builder.append("\t| %-5s | %56s | %-56s |\n".formatted(i, i < clean.size() ? clean.get(i).type() : "", i < dirty.size() ? dirty.get(i).type() : ""));
        }
        builder.append("\t");
        builder.append("=".repeat(127));
        return builder.toString();
    }

    private record TypeWithContext(Type type, int pos) {
        public boolean sameType(TypeWithContext other) {
            return type().equals(other.type());
        }
    }

    private static class ParamDiffBuilder {
        private final ImmutableList.Builder<Pair<Integer, Type>> inserted = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Type>> replaced = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Integer>> swapped = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Integer>> moved = ImmutableList.builder();
        private final ImmutableList.Builder<Integer> removed = ImmutableList.builder();

        public ParamDiffBuilder insert(int index, Type type) {
            if (DEBUG) {
                LOGGER.info("Inserting {} at {}", type, index);
            }
            this.inserted.add(Pair.of(index, type));
            return this;
        }

        public ParamDiffBuilder replace(int index, Type type) {
            this.replaced.add(Pair.of(index, type));
            return this;
        }

        public ParamDiffBuilder swap(int from, int to) {
            this.swapped.add(Pair.of(from, to));
            return this;
        }

        public ParamDiffBuilder move(int from, int to) {
            this.moved.add(Pair.of(from, to));
            return this;
        }

        public ParamDiffBuilder remove(int index) {
            if (DEBUG) {
                LOGGER.info("Removing param at {}", index);
            }
            this.removed.add(index);
            return this;
        }

        public ParamDiffBuilder merge(ParametersDiff diff) {
            return merge(diff, 0);
        }

        public ParamDiffBuilder merge(ParametersDiff diff, int indexOffset) {
            this.inserted.addAll(diff.insertions().stream().map(p -> p.mapFirst(i -> i + indexOffset)).toList());
            this.replaced.addAll(diff.replacements().stream().map(p -> p.mapFirst(i -> i + indexOffset)).toList());
            this.swapped.addAll(diff.swaps().stream().map(p -> p.mapFirst(i -> i + indexOffset).mapSecond(i -> i + indexOffset)).toList());
            this.removed.addAll(diff.removals().stream().map(i -> i + indexOffset).toList());
            return this;
        }

        public List<Integer> getRemoved() {
            return this.removed.build();
        }

        public ParametersDiff build() {
            return build(-1);
        }

        public ParametersDiff build(int originalCount) {
            List<Pair<Integer, Type>> sortedInsertions = this.inserted.build().stream().sorted(Comparator.comparingInt(Pair::getFirst)).toList();
            return new ParametersDiff(originalCount, sortedInsertions, this.replaced.build(), this.swapped.build(), this.removed.build(), this.moved.build());
        }
    }
}
