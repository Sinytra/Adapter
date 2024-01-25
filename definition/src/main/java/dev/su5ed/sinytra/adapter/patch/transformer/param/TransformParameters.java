package dev.su5ed.sinytra.adapter.patch.transformer.param;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public record TransformParameters(List<ParameterTransformer> transformers, boolean withOffset) implements MethodTransform {
    private static final BiMap<String, Codec<? extends ParameterTransformer>> TRANSFORMER_CODECS = ImmutableBiMap.<String, Codec<? extends ParameterTransformer>>builder()
            .put("inject_parameter", InjectParameterTransform.CODEC)
            .put("swap_parameters", SwapParametersTransformer.CODEC)
            .put("substitute_parameters", SubstituteParameterTransformer.CODEC)
            .put("remove_parameter", RemoveParameterTransformer.CODEC)
            .build();

    public static final Codec<TransformParameters> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.STRING
                    .<ParameterTransformer>dispatch(c -> TRANSFORMER_CODECS.inverse().get(c.codec()), TRANSFORMER_CODECS::get)
                    .listOf()
                    .fieldOf("transformers")
                    .forGetter(TransformParameters::transformers),
            Codec.BOOL.optionalFieldOf("withOffset", false).forGetter(TransformParameters::withOffset)
    ).apply(in, TransformParameters::new));

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        boolean isStatic = methodContext.isStatic(methodNode);
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        Patch.Result result = Patch.Result.PASS;

        AnnotationHandle annotation = methodContext.methodAnnotation();
        boolean needsLocalOffset = annotation.matchesDesc(MixinConstants.REDIRECT) || annotation.matchesDesc(MixinConstants.WRAP_OPERATION);
        // If it's a redirect, the first local variable (index 1) is the object instance
        int offset = !isStatic && withOffset && needsLocalOffset ? 1 : 0;

        for (ParameterTransformer transform : transformers) {
            result = result.or(transform.apply(classNode, methodNode, methodContext, context, newParameterTypes, offset));
        }

        methodContext.updateDescription(classNode, methodNode, newParameterTypes);

        return result;
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @CanIgnoreReturnValue
    public static class Builder {
        private final List<ParameterTransformer> transformers = new ArrayList<>();
        private boolean offset = false;

        public Builder transform(ParameterTransformer transformer) {
            this.transformers.add(transformer);
            return this;
        }

        public Builder inject(int parameterIndex, Type type) {
            return this.transform(new InjectParameterTransform(parameterIndex, type));
        }

        public Builder swap(int from, int to) {
            return this.transform(new SwapParametersTransformer(from, to));
        }

        public Builder substitute(int target, int substitute) {
            return this.transform(new SubstituteParameterTransformer(target, substitute));
        }

        public Builder inline(int target, Consumer<InstructionAdapter> adapter) {
            return this.transform(new InlineParameterTransformer(target, adapter));
        }

        public Builder remove(int index) {
            return this.transform(new RemoveParameterTransformer(index));
        }

        public Builder withOffset() {
            this.offset = true;
            return this;
        }

        @CheckReturnValue
        public TransformParameters build() {
            return new TransformParameters(transformers, offset);
        }
    }
}
