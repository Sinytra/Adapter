package dev.su5ed.sinytra.adapter.patch;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public record ParametersDiff(int originalCount, List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements, List<Pair<Integer, Integer>> swaps) {
    public static final class MethodParameter {
        private final Type type;
        private final boolean isGeneratedType;

        public MethodParameter(@Nullable String name, Type type) {
            this.type = type;
            this.isGeneratedType = name != null && AdapterUtil.isGeneratedVariableName(name, type);
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
            return new ParametersDiff(-1, List.of(), List.of(), List.of());
        }

        int cleanParamCount = Type.getArgumentTypes(clean.desc).length;
        int dirtyParamCount = Type.getArgumentTypes(dirty.desc).length;
        boolean isStatic = (clean.access & Opcodes.ACC_STATIC) != 0;
        // Get params as local variables, which include their names as well
        List<MethodParameter> cleanParams = clean.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isStatic || lv.index != 0)
            .limit(cleanParamCount)
            .map(MethodParameter::new)
            .toList();
        List<MethodParameter> dirtyParams = dirty.localVariables.stream()
            .sorted(Comparator.comparingInt(lv -> lv.index))
            .filter(lv -> isStatic || lv.index != 0)
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
        int i = 0;
        int j = 0;
        int lvtIndex = 0;
        // New params are expected to be at least the same size as the old ones, so we use them for the outer loop
        outer:
        while (j < dirtyParameters.size()) {
            // Start iterating over the original params
            if (i < cleanParameters.size()) {
                MethodParameter cleanParam = cleanParameters.get(i);
                MethodParameter dirtyParam = dirtyParameters.get(j);
                // Check if old and new params at this index are the same
                boolean sameType = cleanParam.type.equals(dirtyParam.type);
                if (!sameType || !cleanParam.matchName(dirtyParam)) {
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
                    // If the param is not found, then it was likely replaced
                    if (!cleanParam.type.equals(dirtyParam.type)) {
                        replacements.add(Pair.of(lvtIndex, dirtyParam.type));
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
            j++;
        }
        if (j - i != insertions.size()) {
            throw new IllegalStateException("Unexpected difference in params size");
        }
        return new ParametersDiff(i, insertions, replacements, List.of());
    }

    public boolean isEmpty() {
        return this.insertions.isEmpty() && this.replacements.isEmpty();
    }
}
