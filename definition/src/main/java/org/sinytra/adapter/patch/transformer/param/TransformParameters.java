package org.sinytra.adapter.patch.transformer.param;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public record TransformParameters(List<ParameterTransformer> transformers, boolean withOffset, ParamTransformTarget targetType) implements MethodTransform {
    private static final BiMap<String, Codec<? extends ParameterTransformer>> TRANSFORMER_CODECS = ImmutableBiMap.<String, Codec<? extends ParameterTransformer>>builder()
        .put("inject_parameter", InjectParameterTransform.CODEC)
        .put("swap_parameters", SwapParametersTransformer.CODEC)
        .put("substitute_parameters", SubstituteParameterTransformer.CODEC)
        .put("remove_parameter", RemoveParameterTransformer.CODEC)
        .put("replace_parameter", ReplaceParametersTransformer.CODEC)
        .put("move_parameter", MoveParametersTransformer.CODEC)
        .build();

    public static final Codec<TransformParameters> CODEC = RecordCodecBuilder.create(in -> in.group(
        Codec.STRING
            .<ParameterTransformer>dispatch("transformer_type", c -> TRANSFORMER_CODECS.inverse().get(c.codec()), TRANSFORMER_CODECS::get)
            .listOf()
            .fieldOf("transformers")
            .forGetter(TransformParameters::transformers),
        Codec.BOOL.optionalFieldOf("withOffset", false).forGetter(TransformParameters::withOffset),
        ParamTransformTarget.CODEC.optionalFieldOf("targetType", ParamTransformTarget.ALL).forGetter(TransformParameters::targetType)
    ).apply(in, TransformParameters::new));

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return this.targetType.getTargetMixinTypes();
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        boolean isStatic = methodContext.isStatic();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        Patch.Result result = Patch.Result.PASS;

        AnnotationHandle annotation = methodContext.methodAnnotation();
        boolean needsLocalOffset = annotation.matchesDesc(MixinConstants.REDIRECT) || annotation.matchesDesc(MixinConstants.WRAP_OPERATION);
        // If it's a redirect, the first local variable (index 1) is the object instance
        int offset = !isStatic && this.withOffset && needsLocalOffset ? 1 : 0;

        for (ParameterTransformer transform : transformers) {
            result = result.or(transform.apply(classNode, methodNode, methodContext, context, newParameterTypes, offset));
        }

        methodContext.updateDescription(newParameterTypes);

        return result;
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    public static Builder builder() {
        return new Builder();
    }

    @CanIgnoreReturnValue
    public static class Builder {
        private final List<ParameterTransformer> transformers = new ArrayList<>();
        private boolean offset = false;
        private ParamTransformTarget targetType = ParamTransformTarget.ALL;

        public Builder transform(ParameterTransformer transformer) {
            this.transformers.add(transformer);
            return this;
        }

        public Builder transform(List<ParameterTransformer> transformers) {
            this.transformers.addAll(transformers);
            return this;
        }

        public Builder inject(int parameterIndex, Type type) {
            return this.transform(new InjectParameterTransform(parameterIndex, type));
        }

        public Builder replace(int index, Type type) {
            return transform(new ReplaceParametersTransformer(index, type));
        }

        public Builder replacements(List<Pair<Integer, Type>> replacements) {
            replacements.forEach(p -> replace(p.getFirst(), p.getSecond()));
            return this;
        }

        public Builder swap(int from, int to) {
            return transform(new SwapParametersTransformer(from, to));
        }

        public Builder substitute(int target, int substitute) {
            return transform(new SubstituteParameterTransformer(target, substitute));
        }

        public Builder inline(int target, Consumer<InstructionAdapter> adapter) {
            return transform(new InlineParameterTransformer(target, adapter));
        }

        public Builder remove(int index) {
            return transform(new RemoveParameterTransformer(index));
        }

        public Builder withOffset() {
            this.offset = true;
            return this;
        }

        public Builder withOffset(boolean offset) {
            this.offset = offset;
            return this;
        }

        public Builder targetType(ParamTransformTarget targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder chain(Consumer<Builder> consumer) {
            consumer.accept(this);
            return this;
        }

        @CheckReturnValue
        public TransformParameters build() {
            return new TransformParameters(this.transformers, this.offset, this.targetType);
        }
    }
}
