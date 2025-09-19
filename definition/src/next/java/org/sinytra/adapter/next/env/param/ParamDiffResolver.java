package org.sinytra.adapter.next.env.param;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot.*;

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

    public record ParamState(Type type, int cleanIndex, int dirtyIndex) {}
    
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

    public static ParamEvalResult resolve(List<Type> input, LayeredParamsDiffSnapshot diff) {
        List<PositionedParam> state = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Type type = input.get(i);
            state.add(new PositionedParam(i, type));
        }

        // InsertParam, ReplaceParam, SwapParam, MoveParam, RemoveParam, InlineParam, SubstituteParam
        for (LayeredParamsDiffSnapshot.ParamModification modification : diff.modifications()) {
            switch (modification) {
                case InsertParam(int index, Type type) -> state.add(index, new PositionedParam(-1, type));
                case ReplaceParam(int index, Type type) -> state.get(index).type = type;
                case SwapParam(int from, int to) -> {
                    PositionedParam clean = state.get(from);
                    PositionedParam dirty = state.get(to);

                    state.set(from, dirty);
                    state.set(to, clean);
                }
                case MoveParam(int from, int to) -> {
                    state.add(to, state.remove(from));
                }
                case RemoveParam(int index) -> state.remove(index);
                case InlineParam inline -> state.remove(inline.target());
                case SubstituteParam substitute -> state.remove(substitute.target());
                case null, default -> throw new RuntimeException("Unknown parameter modification: " + modification);
            }
        }
        
        List<ParamState> result = new ArrayList<>(state.size());
        for (int i = 0; i < state.size(); i++) {
            PositionedParam param = state.get(i);
            result.add(new ParamState(param.type, param.cleanPos, i));
        }

        return new ParamEvalResult(input, result);
    }
}
