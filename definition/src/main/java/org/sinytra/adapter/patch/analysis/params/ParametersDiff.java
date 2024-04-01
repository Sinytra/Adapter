package org.sinytra.adapter.patch.analysis.params;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.transformer.param.InjectParameterTransform;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.transformer.param.ParameterTransformer;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.GeneratedVariables;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * @deprecated Use {@link EnhancedParamsDiff} where possible
 */
@Deprecated
public record ParametersDiff(int originalCount, List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements, List<Pair<Integer, Integer>> swaps,
                             List<Integer> removals, List<Pair<Integer, Integer>> moves) {
    public static final ParametersDiff EMPTY = new ParametersDiff(-1, List.of(), List.of(), List.of(), List.of(), List.of());

    public List<MethodTransform> createTransforms(ParamTransformTarget type) {
        List<MethodTransform> list = new ArrayList<>();
        SimpleParamsDiffSnapshot light = SimpleParamsDiffSnapshot.createLight(this);
        if (!light.isEmpty()) {
            list.add(new ModifyMethodParams(light, type, false, null));
        }
        if (!this.insertions.isEmpty()) {
            list.add(new TransformParameters(this.insertions.stream()
                .<ParameterTransformer>map(p -> new InjectParameterTransform(p.getFirst(), p.getSecond()))
                .toList(), true, type));
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

    public boolean isEmpty() {
        return this.insertions.isEmpty() && this.replacements.isEmpty() && this.swaps.isEmpty() && this.removals.isEmpty() && this.moves.isEmpty();
    }
}
