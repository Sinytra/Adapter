package org.sinytra.adapter.analysis.params;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.GeneratedVariables;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @deprecated Use {@link EnhancedParamsDiff} where possible
 */
@Deprecated
public record ParametersDiff(
    int originalCount,
    List<Pair<Integer, Type>> insertions,
    List<Pair<Integer, Type>> replacements,
    List<Pair<Integer, Integer>> swaps,
    List<Integer> removals,
    List<Pair<Integer, Integer>> moves
) {
    public record MethodParameter(Type type, boolean isGeneratedType) {
        public MethodParameter(@Nullable String name, Type type) {
            this(type, name != null && GeneratedVariables.isGeneratedVariableName(name, type));
        }

        public boolean matchName(MethodParameter other) {
            return this.isGeneratedType == other.isGeneratedType;
        }
    }

    public LayeredParamsDiffSnapshot toSnapshot() {
        return LayeredParamsDiffSnapshot.builder()
            .insertions(this.insertions)
            .replacements(this.replacements)
            .swaps(this.swaps)
            .removals(this.removals)
            .moves(this.moves)
            .build();
    }

    public static ParametersDiff compareTypeParameters(Type[] parameterTypes, Type[] newParameterTypes) {
        List<MethodParameter> cleanParameters = Stream.of(parameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        List<MethodParameter> dirtyParameters = Stream.of(newParameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        return compareParameters(cleanParameters, dirtyParameters, false);
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

    public boolean isEmpty() {
        return this.insertions.isEmpty() && this.replacements.isEmpty() && this.swaps.isEmpty() && this.removals.isEmpty() && this.moves.isEmpty();
    }
}
