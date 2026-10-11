package org.sinytra.adapter.analysis.params;

import com.google.errorprone.annotations.CheckReturnValue;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.transform.param.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record LayeredParamsDiffSnapshot(List<ParameterTransformer> modifications) {
    public static final LayeredParamsDiffSnapshot EMPTY = new LayeredParamsDiffSnapshot(List.of());

    public boolean isEmpty() {
        return this.modifications.isEmpty();
    }

    public <T extends ParameterTransformer> List<T> modifications(Class<T> type) {
        return this.modifications.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    public List<InjectParameterTransform> insertions() {
        return modifications(InjectParameterTransform.class);
    }

    public List<ReplaceParametersTransformer> replacements() {
        return modifications(ReplaceParametersTransformer.class);
    }

    public List<SwapParametersTransformer> swaps() {
        return modifications(SwapParametersTransformer.class);
    }

    public List<MoveParametersTransformer> moves() {
        return modifications(MoveParametersTransformer.class);
    }

    public List<RemoveParameterTransformer> removals() {
        return modifications(RemoveParameterTransformer.class);
    }

    public LayeredParamsDiffSnapshot offset(int offset) {
        return new LayeredParamsDiffSnapshot(this.modifications.stream().map(m -> m.offset(offset)).toList());
    }

    public TransformParameters asParameterTransformer(boolean withOffset) {
        return TransformParameters.builder().transform(this.modifications).withOffset(withOffset).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ParameterTransformer> modifications = new ArrayList<>();

        private Builder() {
        }

        public Builder insert(int index, Type type) {
            return add(new InjectParameterTransform(index, type));
        }

        public Builder replace(int index, Type type) {
            return add(new ReplaceParametersTransformer(index, type));
        }

        public Builder swap(int from, int to) {
            return add(new SwapParametersTransformer(from, to));
        }

        public Builder substitute(int target, int substitute) {
            return add(new SubstituteParameterTransformer(target, substitute));
        }

        public Builder move(int from, int to) {
            return add(new MoveParametersTransformer(from, to));
        }

        public Builder remove(int index) {
            // Usages of removed parameters are left as they are
            return add(new RemoveParameterTransformer(index, false));
        }

        public Builder inline(int target, Consumer<InstructionAdapter> adapter) {
            return add(new InlineParameterTransformer(target, adapter));
        }

        public Builder merge(LayeredParamsDiffSnapshot diff) {
            return merge(diff, 0);
        }

        public Builder merge(LayeredParamsDiffSnapshot diff, int indexOffset) {
            diff.modifications().forEach(m -> add(m.offset(indexOffset)));
            return this;
        }

        public List<Integer> getRemovals() {
            return this.modifications.stream()
                .filter(RemoveParameterTransformer.class::isInstance)
                .map(m -> ((RemoveParameterTransformer) m).index())
                .toList();
        }

        private Builder add(ParameterTransformer modification) {
            this.modifications.add(modification);
            return this;
        }

        @CheckReturnValue
        public LayeredParamsDiffSnapshot build() {
            return new LayeredParamsDiffSnapshot(List.copyOf(this.modifications));
        }
    }
}
