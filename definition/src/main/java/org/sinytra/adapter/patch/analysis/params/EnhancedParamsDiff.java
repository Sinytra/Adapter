package org.sinytra.adapter.patch.analysis.params;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.util.GeneratedVariables;
import org.slf4j.Logger;

import java.util.*;

public class EnhancedParamsDiff {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = Boolean.getBoolean("adapter.definition.paramdiff.debug");

    public static SimpleParamsDiffSnapshot create(Type[] clean, Type[] dirty) {
        return create(List.of(clean), List.of(dirty));
    }

    public static SimpleParamsDiffSnapshot create(List<Type> clean, List<Type> dirty) {
        SimpleParamsDiffSnapshot.Builder builder = SimpleParamsDiffSnapshot.builder();
        buildDiff(builder, clean, dirty);
        return builder.build();
    }

    public static LayeredParamsDiffSnapshot createLayered(List<Type> clean, List<Type> dirty) {
        LayeredParamsDiffSnapshot.Builder builder = LayeredParamsDiffSnapshot.builder();
        buildDiff(builder, clean, dirty);
        return builder.build();
    }

    public static LayeredParamsDiffSnapshot compareMethodParameters(MethodNode clean, MethodNode dirty) {
        if (clean.localVariables == null || dirty.localVariables == null) {
            return LayeredParamsDiffSnapshot.EMPTY;
        }

        int cleanParamCount = Type.getArgumentTypes(clean.desc).length;
        int dirtyParamCount = Type.getArgumentTypes(dirty.desc).length;
        boolean isCleanStatic = (clean.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        boolean isDirtyStatic = (dirty.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        // Get params as local variables, which include their names as well
        List<LocalVariable> cleanParams = clean.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isCleanStatic || lv.index != 0)
            .limit(cleanParamCount)
            .map(LocalVariable::new)
            .toList();
        List<LocalVariable> dirtyParams = dirty.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isDirtyStatic || lv.index != 0)
            .limit(dirtyParamCount)
            .map(LocalVariable::new)
            .toList();
        boolean compareNames = cleanParams.stream().allMatch(t -> t.name() != null) && dirtyParams.stream().anyMatch(LocalVariable::isGenerated);

        LayeredParamsDiffSnapshot.Builder builder = LayeredParamsDiffSnapshot.builder();
        buildDiffWithContext(builder, createPositionedVariableList(cleanParams), createPositionedVariableList(dirtyParams), compareNames);
        return builder.build();
    }

    private static void buildDiff(ParamsDiffSnapshotBuilder builder, List<Type> clean, List<Type> dirty) {
        buildDiffWithContext(builder, createPositionedList(clean), createPositionedList(dirty), false);
    }

    private static void buildDiffWithContext(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue, boolean compareNames) {
        int dirtySize = dirtyQueue.size();
        boolean sameSize = cleanQueue.size() == dirtyQueue.size();

        if (DEBUG) {
            LOGGER.info("Comparing types:\n{}", printTable(cleanQueue, dirtyQueue));
        }

        while (!cleanQueue.isEmpty()) {
            // Look ahead for matching types at the beginning of the list
            // If the first two are equal, remove the first ones and repeat
            if (predictParameterMatch(builder, cleanQueue, dirtyQueue, compareNames, sameSize)) {
                cleanQueue.remove(0);
                dirtyQueue.remove(0);
                continue;
            }
            // Handle replaced types, needs improving
            if (tryReplacingParams(builder, cleanQueue, dirtyQueue)) {
                continue;
            }
            // Check for swapped / reordered types. Also handles unique-type insertions
            SwapResult swapResult = checkForSwaps(builder, cleanQueue, dirtyQueue);
            if (swapResult != null) {
                dirtyQueue.removeAll(swapResult.removeDirty());
                cleanQueue.clear();
                dirtyQueue.clear();
                break;
            }
            // Find the smallest set of matching types by locating the position of the next clean type in the dirty list
            int dirtyTypeIndex = cleanQueue.size() == 1 ? dirtySize - 1 : lookAhead(dirtyQueue, cleanQueue.get(1));
            if (dirtyTypeIndex != -1) {
                boolean matchesName = !compareNames || cleanQueue.size() < 2 || dirtyQueue.size() < 2 || cleanQueue.get(1).matches(dirtyQueue.get(1));
                int offset = cleanQueue.get(0).sameType(dirtyQueue.get(0)) && matchesName ? 1 : 2;
                List<TypeWithContext> compareClean = extract(cleanQueue, matchesName ? 2 : 1);
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
    }

    private static boolean predictParameterMatch(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue, boolean compareNames, boolean sameSize) {
        // For same-sized comparisons, we can be certain there have been no insertions
        if (sameSize && !cleanQueue.isEmpty() && !dirtyQueue.isEmpty()) {
            if (!cleanQueue.get(0).sameType(dirtyQueue.get(0))) {
                // If the first parameters differ, it's possible one of them has been removed
                // == Clean ==
                // 0 Foo
                // 1 Bar
                // 2 World
                // == Dirty ==
                // 0 Bar
                // 1 World
                // 2 Sheep
                if (cleanQueue.size() > 2 && dirtyQueue.size() > 2 && cleanQueue.get(1).matches(dirtyQueue.get(0)) && cleanQueue.get(2).matches(dirtyQueue.get(1))) {
                    builder.remove(cleanQueue.get(0).pos());
                    cleanQueue.remove(0);
                    return true;
                }
                return false;
            }
            return true;
        } else if (cleanQueue.size() > 1 && dirtyQueue.size() > 1) {
            if (cleanQueue.get(0).sameType(dirtyQueue.get(0))) {
                if (cleanQueue.get(1).matches(dirtyQueue.get(1))) {
                    // If the first two parameters match, we can be sure the first ones are identical without any insertions
                    return true;
                }
                // Comparing names can be useful in cases where a parameter is inserted in between multiple ones of the same type
                else if (compareNames) {
                    // Check for inserted parameters
                    // == Clean ==
                    // 0 F one
                    // 1 F two
                    // 2 F three
                    // == Dirty ==
                    // 0 F one
                    // 1 F <inserted>
                    // 2 F two
                    if (dirtyQueue.size() > 2 && cleanQueue.get(1).matches(dirtyQueue.get(2))) {
                        builder.insert(dirtyQueue.get(1).pos(), dirtyQueue.get(1).type());
                        cleanQueue.remove(0);
                        dirtyQueue.remove(0);
                        dirtyQueue.remove(0);
                        return true;
                    }
                } else {
                    // Check for moved parameters
                    TypeWithContext param = cleanQueue.get(1);
                    TypeWithContext dirtyParam = dirtyQueue.get(1);
                    Map<Type, Integer> cleanGroup = groupTypes(cleanQueue);
                    Map<Type, Integer> dirtyGroup = groupTypes(dirtyQueue);
                    if (cleanGroup.get(param.type()) == 1 && dirtyGroup.getOrDefault(param.type(), 0) == 1
                        // Make sure the next param isn't injected
                        && Objects.equals(cleanGroup.getOrDefault(dirtyParam.type(), 0), dirtyGroup.get(dirtyParam.type()))
                    ) {
                        // The parameter has been moved
                        TypeWithContext newParam = dirtyQueue.stream().filter(t -> t.type().equals(param.type())).findFirst().orElseThrow();
                        if (Math.abs(param.pos() - newParam.pos()) == 1) {
                            builder.swap(param.pos(), newParam.pos());
                        } else {
                            builder.move(param.pos(), newParam.pos());
                        }
                        cleanQueue.remove(param);
                        dirtyQueue.remove(newParam);
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
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

    private static boolean tryReplacingParams(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue) {
        if (replaceType(builder, 0, cleanQueue, dirtyQueue)) {
            return true;
        } else if (cleanQueue.size() == 2 && dirtyQueue.size() == 2) {
            // Handle swapped parameters in simple cases
            if (cleanQueue.get(0).sameType(dirtyQueue.get(1)) && cleanQueue.get(1).sameType(dirtyQueue.get(0))) {
                builder.swap(dirtyQueue.get(0).pos(), dirtyQueue.get(1).pos());
                cleanQueue.clear();
                dirtyQueue.clear();
                return true;
            }
            return replaceType(builder, 1, cleanQueue, dirtyQueue);
        }
        return false;
    }

    private static boolean replaceType(ParamsDiffSnapshotBuilder builder, int index, List<TypeWithContext> cleanQueue, List<TypeWithContext> dirtyQueue) {
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
        for (int i = index; i < cleanQueue.size() && i < dirtyQueue.size(); i++) {
            if (!cleanQueue.get(i).equals(dirtyQueue.get(i))) {
                return true;
            }
        }
        return false;
    }

    private record SwapResult(List<TypeWithContext> removeDirty) {}

    @Nullable
    private static SwapResult checkForSwaps(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        Map<Type, Integer> cleanGroup = groupTypes(clean);
        Map<Type, Integer> dirtyGroup = groupTypes(dirty);
        MapDifference<Type, Integer> diff = Maps.difference(cleanGroup, dirtyGroup);

        List<TypeWithContext> rearrangeClean = new ArrayList<>(clean);
        List<TypeWithContext> rearrangeDirty = new ArrayList<>(dirty);
        List<TypeWithContext> removeDirty = new ArrayList<>();
        SimpleParamsDiffSnapshot.Builder tempDiff = SimpleParamsDiffSnapshot.builder();
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
                    int offset = inserted.pos() + (int) builder.getRemovals().stream().filter(i -> i < inserted.pos()).count();
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
            builder.merge(tempDiff.build());
            // Run rearrangement
            rearrange(builder, rearrangeClean, rearrangeDirty);
            return new SwapResult(removeDirty);
        } else if (rearrangeClean.isEmpty() && rearrangeDirty.isEmpty()) {
            builder.merge(tempDiff.build());
            return new SwapResult(removeDirty);
        }
        return null;
    }

    private static void rearrange(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        record Rearrangement(TypeWithContext cleanType, TypeWithContext dirtyType, int fromRelative, int toRelative) {}

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
            // The original pos in the dirty list might differ due to prior insertions
            int offsetOriginalPos = rearrangeDirty.get(rearrangement.fromRelative()).pos();
            int destPos = rearrangement.dirtyType().pos();
            if (Math.abs(offsetOriginalPos - destPos) == 1) {
                builder.swap(offsetOriginalPos, destPos);
            } else {
                builder.move(offsetOriginalPos, destPos);
            }
            rearrangeClean.add(rearrangement.toRelative(), rearrangeClean.remove(rearrangement.fromRelative()));
        }
    }

    private static void compare(ParamsDiffSnapshotBuilder builder, List<TypeWithContext> clean, List<TypeWithContext> dirty) {
        if (DEBUG) {
            LOGGER.info("Running comparison for:\n{}", printTable(clean, dirty));
        }

        List<ParametersDiff.MethodParameter> cleanTypes = clean.stream().map(t -> new ParametersDiff.MethodParameter(t.type(), t.isGenerated())).toList();
        List<ParametersDiff.MethodParameter> dirtyTypes = dirty.stream().map(t -> new ParametersDiff.MethodParameter(t.type(), t.isGenerated())).toList();
        ParametersDiff diff = ParametersDiff.compareParameters(cleanTypes, dirtyTypes, false);
        if (DEBUG) {
            LOGGER.info("Comparison results:\n\tInserted: {}\n\tReplaced: {}\n\tSwapped:  {}\n\tRemoved:  {}", diff.insertions(), diff.removals(), diff.swaps(), diff.removals());
        }
        int indexOffset = !dirty.isEmpty() ? dirty.get(0).pos() : 0;
        builder.merge(SimpleParamsDiffSnapshot.create(diff), indexOffset);
    }

    private static List<TypeWithContext> createPositionedList(List<Type> list) {
        List<TypeWithContext> ret = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ret.add(new TypeWithContext(list.get(i), i));
        }
        return ret;
    }

    private static List<TypeWithContext> createPositionedVariableList(List<LocalVariable> list) {
        List<TypeWithContext> ret = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            LocalVariable ctx = list.get(i);
            ret.add(new TypeWithContext(ctx.name(), ctx.type(), i, ctx.isGenerated()));
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

    private record LocalVariable(@Nullable String name, Type type, boolean isGenerated) {
        public LocalVariable(LocalVariableNode lvn) {
            this(lvn.name, Type.getType(lvn.desc), lvn.name != null && GeneratedVariables.isGeneratedVariableName(lvn.name, Type.getType(lvn.desc)));
        }
    }

    private record TypeWithContext(@Nullable String name, Type type, int pos, boolean isGenerated) {
        public TypeWithContext(Type type, int pos) {
            this(null, type, pos, false);
        }

        public boolean sameType(TypeWithContext other) {
            return type().equals(other.type());
        }

        public boolean sameName(TypeWithContext other) {
            return this.name == null || other.name() == null || this.isGenerated == other.isGenerated();
        }

        public boolean matches(TypeWithContext other) {
            return sameType(other) && sameName(other);
        }
    }
}
