package dev.su5ed.sinytra.adapter.patch;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public record ParametersDiff(int originalCount, List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements) {
    private static final String PARAM_NAME_PREFIX = "p_";

    record MethodParameter(@Nullable String name, Type type) {
        public MethodParameter(LocalVariableNode lv) {
            this(lv.name, Type.getType(lv.desc));
        }
    }

    public static ParametersDiff compareMethodParameters(MethodNode clean, MethodNode dirty) {
        if (clean.localVariables == null || dirty.localVariables == null) {
            return new ParametersDiff(-1, List.of(), List.of());
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
        return compareParameters(cleanParams, dirtyParams, (c, d) -> c.name.startsWith(PARAM_NAME_PREFIX) && !d.name.startsWith(PARAM_NAME_PREFIX));
    }

    public static ParametersDiff compareTypeParameters(Type[] parameterTypes, Type[] newParameterTypes) {
        List<MethodParameter> cleanParameters = Stream.of(parameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        List<MethodParameter> dirtyParameters = Stream.of(newParameterTypes)
            .map(type -> new MethodParameter(null, type))
            .toList();
        return compareParameters(cleanParameters, dirtyParameters, (c, d) -> !c.type.equals(d.type));
    }

    private static ParametersDiff compareParameters(List<MethodParameter> cleanParameters, List<MethodParameter> dirtyParameters, BiPredicate<MethodParameter, MethodParameter> predicate) {
        // Indexes we insert new params at
        List<Pair<Integer, Type>> insertions = new ArrayList<>();
        // Indexes we replace params at
        List<Pair<Integer, Type>> replacements = new ArrayList<>();
        int i = 0;
        int j = 0;
        // New params are expected to be at least the same size as the old ones, so we use them for the outer loop
        outer:
        while (j < dirtyParameters.size()) {
            // Start iterating over the original params
            if (i < cleanParameters.size()) {
                MethodParameter cleanParam = cleanParameters.get(i);
                MethodParameter dirtyParam = dirtyParameters.get(j);
                // Check if old and new params at this index are the same
                if (predicate.test(cleanParam, dirtyParam)) {
                    // If not, it is possible a new param was injected onto this index.
                    // In that case, the original param was moved further down the array, and we must find it.
                    for (int k = j + 1; k < dirtyParameters.size(); k++) {
                        if (cleanParam.equals(dirtyParameters.get(k))) {
                            // If the param is found, add all parameters between the original and new pos to the insertion list
                            for (; j < k; j++) {
                                insertions.add(Pair.of(j, dirtyParameters.get(j).type));
                            }
                            // Continue onto the next params
                            continue outer;
                        }
                    }
                    // If the param is not found, then it was likely replaced
                    if (dirtyParameters.size() != cleanParameters.size() || !cleanParam.type.equals(dirtyParam.type)) {
                        replacements.add(Pair.of(j, dirtyParam.type));
                    }
                }
                i++;
            }
            // For appending parameters at the end of the list
            else {
                insertions.add(Pair.of(j, dirtyParameters.get(j).type));
            }
            j++;
        }
        if (j - i != insertions.size()) {
            throw new IllegalStateException("Unexpected difference in params size");
        }
        return new ParametersDiff(i, insertions, replacements);
    }
    
    public boolean isEmpty() {
        return this.insertions.isEmpty() && this.replacements.isEmpty();
    }
}
