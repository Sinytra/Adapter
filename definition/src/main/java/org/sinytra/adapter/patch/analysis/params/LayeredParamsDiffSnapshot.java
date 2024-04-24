package org.sinytra.adapter.patch.analysis.params;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.param.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public record LayeredParamsDiffSnapshot(List<ParamModification> modifications) implements ParamsDiffSnapshot {
    public static LayeredParamsDiffSnapshot EMPTY = new LayeredParamsDiffSnapshot(List.of());

    public interface ParamModification {
        ParamModification offset(int offset);

        boolean satisfiesIndexLimit(int index);

        ParameterTransformer asParameterTransformer();
    }

    record InsertParam(int index, Type type) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new InsertParam(this.index + offset, this.type);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.index < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new InjectParameterTransform(this.index, this.type);
        }
    }

    record ReplaceParam(int index, Type type) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new ReplaceParam(this.index + offset, this.type);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.index < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new ReplaceParametersTransformer(this.index, this.type);
        }
    }

    record SwapParam(int from, int to) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new SwapParam(this.from + offset, this.to + offset);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.from < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new SwapParametersTransformer(this.from, this.to);
        }
    }

    record MoveParam(int from, int to) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new MoveParam(this.from + offset, this.to + offset);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.from < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new MoveParametersTransformer(this.from, this.to);
        }
    }

    record RemoveParam(int index) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new RemoveParam(this.index + offset);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.index < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new RemoveParameterTransformer(this.index);
        }
    }

    record InlineParam(int target, Consumer<InstructionAdapter> adapter) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new InlineParam(this.target + offset, this.adapter);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.target < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new InlineParameterTransformer(this.target, this.adapter);
        }
    }

    record SubstituteParam(int target, int substitute) implements ParamModification {
        @Override
        public ParamModification offset(int offset) {
            return new SubstituteParam(this.target + offset, this.substitute + offset);
        }

        @Override
        public boolean satisfiesIndexLimit(int index) {
            return this.target < index;
        }

        @Override
        public ParameterTransformer asParameterTransformer() {
            return new SubstituteParameterTransformer(this.target, this.substitute);
        }
    }

    @Override
    public boolean isEmpty() {
        return this.modifications.isEmpty();
    }

    @Override
    public List<Pair<Integer, Type>> insertions() {
        return this.modifications.stream()
            .map(p -> p instanceof InsertParam param ? param : null)
            .filter(Objects::nonNull)
            .map(p -> Pair.of(p.index, p.type))
            .toList();
    }

    @Override
    public List<Pair<Integer, Type>> replacements() {
        return this.modifications.stream()
            .map(p -> p instanceof ReplaceParam param ? param : null)
            .filter(Objects::nonNull)
            .map(p -> Pair.of(p.index, p.type))
            .toList();
    }

    public List<Pair<Integer, Integer>> swaps() {
        return this.modifications.stream()
            .map(p -> p instanceof SwapParam param ? param : null)
            .filter(Objects::nonNull)
            .map(p -> Pair.of(p.from(), p.to()))
            .toList();
    }

    public List<Pair<Integer, Integer>> moves() {
        return this.modifications.stream()
            .map(p -> p instanceof MoveParam param ? param : null)
            .filter(Objects::nonNull)
            .map(p -> Pair.of(p.from(), p.to()))
            .toList();
    }

    @Override
    public List<Integer> removals() {
        return this.modifications.stream()
            .map(p -> p instanceof RemoveParam param ? param.index : null)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public LayeredParamsDiffSnapshot offset(int offset, int limit) {
        return new LayeredParamsDiffSnapshot(this.modifications.stream().filter(p -> p.satisfiesIndexLimit(limit)).map(p -> p.offset(offset)).toList());
    }

    @Override
    public MethodTransform asParameterTransformer(ParamTransformTarget type, boolean withOffset, boolean upgradeWrapOperation) {
        List<ParameterTransformer> transformers = this.modifications.stream().map(ParamModification::asParameterTransformer).toList();
        return TransformParameters.builder().transform(transformers).withOffset(withOffset).targetType(type).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements ParamsDiffSnapshotBuilder {
        private final ImmutableList.Builder<ParamModification> modifications = ImmutableList.builder();
        private final List<Integer> removals = new ArrayList<>();

        private Builder() {
        }

        @Override
        public Builder insert(int index, Type type) {
            this.modifications.add(new InsertParam(index, type));
            return this;
        }

        @Override
        public Builder insertions(List<Pair<Integer, Type>> insertions) {
            insertions.forEach(p -> insert(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder replace(int index, Type type) {
            this.modifications.add(new ReplaceParam(index, type));
            return this;
        }

        @Override
        public Builder replacements(List<Pair<Integer, Type>> replacements) {
            replacements.forEach(p -> replace(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder swap(int from, int to) {
            this.modifications.add(new SwapParam(from, to));
            return this;
        }

        @Override
        public Builder swaps(List<Pair<Integer, Integer>> swaps) {
            swaps.forEach(p -> swap(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder substitute(int target, int substitute) {
            this.modifications.add(new SubstituteParam(target, substitute));
            return this;
        }

        @Override
        public Builder substitutes(List<Pair<Integer, Integer>> substitutes) {
            substitutes.forEach(p -> substitute(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder move(int from, int to) {
            this.modifications.add(new MoveParam(from, to));
            return this;
        }

        @Override
        public Builder moves(List<Pair<Integer, Integer>> moves) {
            moves.forEach(p -> move(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder remove(int index) {
            this.modifications.add(new RemoveParam(index));
            this.removals.add(index);
            return this;
        }

        @Override
        public Builder removals(List<Integer> removals) {
            removals.forEach(this::remove);
            return this;
        }

        @Override
        public Builder inline(int target, Consumer<InstructionAdapter> adapter) {
            this.modifications.add(new InlineParam(target, adapter));
            return this;
        }

        @Override
        public Builder inlines(List<Pair<Integer, Consumer<InstructionAdapter>>> inlines) {
            inlines.forEach(p -> inline(p.getFirst(), p.getSecond()));
            return this;
        }

        @Override
        public Builder merge(SimpleParamsDiffSnapshot diff) {
            return merge(diff, 0);
        }

        @Override
        public Builder merge(SimpleParamsDiffSnapshot diff, int indexOffset) {
            diff.insertions().forEach(p -> insert(p.getFirst() + indexOffset, p.getSecond()));
            diff.replacements().forEach(p -> replace(p.getFirst() + indexOffset, p.getSecond()));
            diff.swaps().forEach(p -> swap(p.getFirst() + indexOffset, p.getSecond() + indexOffset));
            diff.substitutes().forEach(p -> substitute(p.getFirst() + indexOffset, p.getSecond() + indexOffset));
            diff.removals().forEach(p -> remove(p + indexOffset));
            diff.moves().forEach(p -> move(p.getFirst() + indexOffset, p.getSecond() + indexOffset));
            diff.inlines().forEach(p -> inline(p.getFirst() + indexOffset, p.getSecond()));
            return this;
        }

        @Override
        public List<Integer> getRemovals() {
            return List.copyOf(this.removals);
        }

        @CheckReturnValue
        public LayeredParamsDiffSnapshot build() {
            return new LayeredParamsDiffSnapshot(this.modifications.build());
        }
    }
}
