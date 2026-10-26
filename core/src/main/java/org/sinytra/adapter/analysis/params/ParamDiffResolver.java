package org.sinytra.adapter.analysis.params;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.edit.param.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParamDiffResolver {
    private static class PositionedParam {
        private final int cleanPos;
        private Type type;

        public PositionedParam(int cleanPos, Type type) {
            this.cleanPos = cleanPos;
            this.type = type;
        }
    }

    public record ParamState(Type type, int cleanIndex, int dirtyIndex) {
    }

    public static class ParamEvalResult {
        private final List<Type> original;
        private final Map<Integer, ParamState> params;

        public ParamEvalResult(List<Type> original, List<ParamState> params) {
            this.original = new ReferenceArrayList<>(original);
            this.params = params.stream()
                .filter(s -> s.cleanIndex != -1)
                .collect(Collectors.toUnmodifiableMap(p -> p.cleanIndex, Function.identity()));
        }

        @Nullable
        public ParamState getUpdated(Type param) {
            int oldIndex = this.original.indexOf(param);
            return oldIndex == -1 ? null : this.params.get(oldIndex);
        }
    }

    public static List<Type> applyTypes(List<Type> input, LayeredParamsDiffSnapshot diff) {
        return simulate(input, diff).stream().map(param -> param.type).toList();
    }

    public static ParamEvalResult resolve(List<Type> input, LayeredParamsDiffSnapshot diff) {
        List<PositionedParam> state = simulate(input, diff);

        List<ParamState> result = new ArrayList<>();
        for (int i = 0; i < state.size(); i++) {
            PositionedParam param = state.get(i);
            result.add(new ParamState(param.type, param.cleanPos, i));
        }

        return new ParamEvalResult(input, result);
    }

    private static List<PositionedParam> simulate(List<Type> input, LayeredParamsDiffSnapshot diff) {
        List<PositionedParam> state = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Type type = input.get(i);
            state.add(new PositionedParam(i, type));
        }

        // InsertParam, ReplaceParam, SwapParam, MoveParam, RemoveParam, InlineParam, SubstituteParam
        for (ParameterTransformer modification : diff.modifications()) {
            switch (modification) {
                case InjectParameterTransform(int index, Type type) -> state.add(index, new PositionedParam(-1, type));
                case ReplaceParametersTransformer replace -> state.get(replace.index()).type = replace.type();
                case SwapParametersTransformer(int from, int to) -> {
                    PositionedParam clean = state.get(from);
                    PositionedParam dirty = state.get(to);

                    state.set(from, dirty);
                    state.set(to, clean);
                }
                case MoveParametersTransformer(int from, int to) -> state.add(to, state.remove(from));
                case RemoveParameterTransformer remove -> state.remove(remove.index());
                case null, default -> throw new RuntimeException("Unknown parameter modification: " + modification);
            }
        }
        return state;
    }
}
