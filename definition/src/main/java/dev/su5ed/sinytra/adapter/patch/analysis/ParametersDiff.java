package dev.su5ed.sinytra.adapter.patch.analysis;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.adapter.patch.transformer.param.InjectParameterTransform;
import dev.su5ed.sinytra.adapter.patch.transformer.param.ParameterTransformer;
import dev.su5ed.sinytra.adapter.patch.transformer.param.TransformParameters;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.GeneratedVariables;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public record ParametersDiff(int originalCount, List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements, List<Pair<Integer, Integer>> swaps,
                             List<Integer> removals, List<Pair<Integer, Integer>> moves) {
    public static final ParametersDiff EMPTY = new ParametersDiff(-1, List.of(), List.of(), List.of(), List.of(), List.of());

    public List<MethodTransform> createTransforms(ModifyMethodParams.TargetType type) {
        final var list = new ArrayList<MethodTransform>();
        var light = ModifyMethodParams.ParamsContext.createLight(this);
        if (!light.isEmpty()) {
            list.add(new ModifyMethodParams(
                    light,
                    type, false, null
            ));
        }
        if (!insertions.isEmpty()) {
            list.add(new TransformParameters(insertions.stream()
                    .<ParameterTransformer>map(p -> new InjectParameterTransform(p.getFirst(), p.getSecond()))
                    .toList(), true));
        }
        return list;
    }

    public record MethodParameter(Type type, boolean isGeneratedType) {
        public MethodParameter(@Nullable String name, Type type) {
            this(type, name != null && GeneratedVariables.isGeneratedVariableName(name, type));
        }

        public MethodParameter(LocalVariableNode lv) {
            this(lv.name, Type.getType(lv.desc));
        }

        public boolean matchName(MethodParameter other) {
            return this.isGeneratedType == other.isGeneratedType;
        }
    }

    public static ParametersDiff compareMethodParameters(MethodNode clean, MethodNode dirty) {
        if (clean.localVariables == null || dirty.localVariables == null) {
            return EMPTY;
        }

        int cleanParamCount = Type.getArgumentTypes(clean.desc).length;
        int dirtyParamCount = Type.getArgumentTypes(dirty.desc).length;
        boolean isCleanStatic = (clean.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        boolean isDirtyStatic = (dirty.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        // Get params as local variables, which include their names as well
        List<MethodParameter> cleanParams = clean.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isCleanStatic || lv.index != 0)
            .limit(cleanParamCount)
            .map(MethodParameter::new)
            .toList();
        List<MethodParameter> dirtyParams = dirty.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isDirtyStatic || lv.index != 0)
            .limit(dirtyParamCount)
            .map(MethodParameter::new)
            .toList();
        return compareParameters(cleanParams, dirtyParams, false);
    }

    public static ParametersDiff compareTypeParameters(Type[] parameterTypes, Type[] newParameterTypes) {
        return compareTypeParameters(parameterTypes, newParameterTypes, false);
    }

    public static ParametersDiff compareTypeParameters(Type[] parameterTypes, Type[] newParameterTypes, boolean lvtIndexes) {
        List<MethodParameter> cleanParameters = Stream.of(parameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        List<MethodParameter> dirtyParameters = Stream.of(newParameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        return compareParameters(cleanParameters, dirtyParameters, lvtIndexes);
    }

    public static ParametersDiff compareParameters(List<MethodParameter> cleanParameters, List<MethodParameter> dirtyParameters, boolean lvtIndexes) {
        // Indexes we insert new params at
        List<Pair<Integer, Type>> insertions = new ArrayList<>();
        // Indexes we replace params at
        List<Pair<Integer, Type>> replacements = new ArrayList<>();
        // Indexes to swap one for another
        List<Pair<Integer, Integer>> swaps = new ArrayList<>();
        List<Integer> removals = new ArrayList<>();
        int i = 0;
        int j = 0;
        int lvtIndex = 0;
        // New params are expected to be at least the same size as the old ones, so we use them for the outer loop
        outer:
        while (j < dirtyParameters.size()) {
            boolean skipJIncr = false;
            // Start iterating over the original params
            if (i < cleanParameters.size()) {
                MethodParameter cleanParam = cleanParameters.get(i);
                MethodParameter dirtyParam = dirtyParameters.get(j);
                // Check if old and new params at this index are the same
                boolean sameType = cleanParam.type.equals(dirtyParam.type);
                if (!sameType || !cleanParam.matchName(dirtyParam)) {
                    boolean handled = false;
                    boolean removing = false;
                    // Check if the params have been swapped
                    if (i + 1 < cleanParameters.size() && j + 1 < dirtyParameters.size()) {
                        MethodParameter nextCleanParam = cleanParameters.get(i + 1);
                        MethodParameter nextDirtyParam = dirtyParameters.get(j + 1);
                        // Detect swapped params
                        if (nextCleanParam.type.equals(dirtyParam.type) && nextDirtyParam.equals(cleanParam)) {
                            swaps.add(Pair.of(j, j + 1));
                            i++;
                            lvtIndex++;
                            j++;
                            handled = true;
                        }
                        // Detect removed parameters, check the next 2 params for matching types (if possible)
                        if (nextCleanParam.equals(dirtyParam) && (j + 2 >= cleanParameters.size() || cleanParameters.get(j + 2).equals(nextDirtyParam))) {
                            removing = true;
                        }
                    }
                    if (!handled) {
                        // If not, it is possible a new param was injected onto this index.
                        // In that case, the original param was moved further down the array, and we must find it.
                        for (int k = j + 1; k < dirtyParameters.size(); k++) {
                            MethodParameter dirtyParamAhead = dirtyParameters.get(k);
                            if (cleanParam.type.equals(dirtyParamAhead.type) && (sameType || cleanParam.matchName(dirtyParamAhead))) {
                                // If the param is found, add all parameters between the original and new pos to the insertion list
                                for (; j < k; j++, lvtIndex++) {
                                    insertions.add(Pair.of(lvtIndex, dirtyParameters.get(j).type));
                                }
                                // Continue onto the next params
                                continue outer;
                            }
                        }
                        if (removing) {
                            removals.add(j);
                            skipJIncr = true;
                        }
                        // If the param is not found, then it was likely replaced
                        else if (!cleanParam.type.equals(dirtyParam.type)) {
                            replacements.add(Pair.of(lvtIndex, dirtyParam.type));
                        }
                    }
                }
                i++;
                lvtIndex += lvtIndexes ? AdapterUtil.getLVTOffsetForType(dirtyParam.type) : 1;
            }
            // For appending parameters at the end of the list
            else {
                Type type = dirtyParameters.get(j).type;
                insertions.add(Pair.of(lvtIndex, type));
                lvtIndex += lvtIndexes ? AdapterUtil.getLVTOffsetForType(type) : 1;
            }
            if (!skipJIncr) {
                j++;
            }
        }
        if (j - i + removals.size() != insertions.size()) {
            throw new IllegalStateException("Unexpected difference in params size");
        }
        return new ParametersDiff(i, insertions, replacements, swaps, removals, List.of());
    }

    public static ParametersDiff rearrangeParameters(List<Type> parameterTypes, List<Type> newParameterTypes) {
        Object2IntMap<Type> typeCount = new Object2IntOpenHashMap<>();
        ListMultimap<Type, Integer> typeIndices = ArrayListMultimap.create();
        for (int i = 0; i < parameterTypes.size(); i++) {
            Type type = parameterTypes.get(i);
            typeCount.put(type, typeCount.getInt(type) + 1);
            typeIndices.put(type, i);
        }
        Object2IntMap<Type> newTypeCount = new Object2IntOpenHashMap<>();
        for (Type type : newParameterTypes) {
            newTypeCount.put(type, newTypeCount.getInt(type) + 1);
        }

        for (Object2IntMap.Entry<Type> entry : typeCount.object2IntEntrySet()) {
            if (newTypeCount.getInt(entry.getKey()) != entry.getIntValue()) {
                return null;
            }
        }

        List<Pair<Integer, Type>> insertions = new ArrayList<>();
        for (int i = 0; i < newParameterTypes.size(); i++) {
            Type type = newParameterTypes.get(i);
            if (!typeCount.containsKey(type)) {
                insertions.add(Pair.of(i, type));
            }
        }

        Object2IntMap<Type> seenTypes = new Object2IntOpenHashMap<>();
        Int2IntMap swaps = new Int2IntLinkedOpenHashMap();
        for (int i = 0; i < newParameterTypes.size(); i++) {
            Type type = newParameterTypes.get(i);
            if (typeIndices.containsKey(type)) {
                List<Integer> indices = typeIndices.get(type);
                int seen = seenTypes.getInt(type);
                int oldIndex = indices.get(seen);
                seenTypes.put(type, seen + 1);
                if (oldIndex != i && !swaps.containsKey(i)) {
                    swaps.put(oldIndex, i);
                }
            }
        }

        if (swaps.isEmpty()) {
            return null;
        }

        List<Pair<Integer, Integer>> swapsList = new ArrayList<>();
        swaps.forEach((from, to) -> swapsList.add(Pair.of(from, to)));
        return new ParametersDiff(parameterTypes.size(), insertions, List.of(), swapsList, List.of(), List.of());
    }

    public ParametersDiff offset(int offset, int limit) {
        List<Pair<Integer, Type>> offsetInsertions = this.insertions.stream().filter(pair -> pair.getFirst() < limit).map(pair -> pair.mapFirst(i -> i + offset)).toList();
        List<Pair<Integer, Type>> offsetReplacements = this.replacements.stream().filter(pair -> pair.getFirst() < limit).map(pair -> pair.mapFirst(i -> i + offset)).toList();
        List<Pair<Integer, Integer>> offsetSwaps = this.swaps.stream().filter(pair -> pair.getFirst() < limit).map(pair -> pair.mapFirst(i -> i + offset).mapSecond(i -> i + offset)).toList();
        List<Pair<Integer, Integer>> offsetMoves = this.moves.stream().filter(pair -> pair.getFirst() < limit).map(pair -> pair.mapFirst(i -> i + offset).mapSecond(i -> i + offset)).toList();
        List<Integer> offsetRemovals = this.removals.stream().filter(i -> i < limit).map(i -> i + offset).toList();
        return new ParametersDiff(this.originalCount, offsetInsertions, offsetReplacements, offsetSwaps, offsetRemovals, offsetMoves);
    }

    public boolean isEmpty() {
        return this.insertions.isEmpty() && this.replacements.isEmpty() && this.swaps.isEmpty() && this.removals.isEmpty() && this.moves.isEmpty();
    }
}
