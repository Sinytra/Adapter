package org.sinytra.adapter.patch.transformer.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.transformer.serialization.MethodTransformFilterSerialization;
import org.sinytra.adapter.patch.transformer.serialization.MethodTransformSerialization;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MethodTransformationPipeline implements MethodTransform {
    public static final Codec<MethodTransformationPipeline> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MethodTransformSerialization.METHOD_TRANSFORM_CODEC.fieldOf("transform").forGetter(m -> m.transform),
        MethodTransformFilterSerialization.METHOD_TRANSFORM_FILTER_CODEC.listOf().fieldOf("filters").forGetter(m -> m.filters)
    ).apply(instance, MethodTransformationPipeline::new));

    private final MethodTransform transform;
    private final List<Supplier<MethodTransform>> onSuccess;
    private final List<Supplier<MethodTransform>> onFail;
    private final List<MethodTransformFilter> filters;

    private MethodTransformationPipeline(MethodTransform transform, List<MethodTransformFilter> filters) {
        this(transform, List.of(), List.of(), filters);
    }

    private MethodTransformationPipeline(MethodTransform transform, List<Supplier<MethodTransform>> onSuccess, List<Supplier<MethodTransform>> onFail, List<MethodTransformFilter> filters) {
        this.transform = transform;
        this.onSuccess = onSuccess;
        this.onFail = onFail;
        this.filters = filters;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        for (MethodTransformFilter filter : filters) {
            if (!filter.test(methodContext)) {
                return Patch.Result.PASS;
            }
        }
        Patch.Result result = this.transform.apply(methodContext);
        for (Supplier<MethodTransform> supplier : result == Patch.Result.PASS ? this.onFail : this.onSuccess) {
            result = result.or(supplier.get().apply(methodContext));
        }
        return result;
    }

    public static Builder builder(MethodTransform transform) {
        return new Builder(transform);
    }

    public static Builder builder(Consumer<MethodTransformBuilder<?>> consumer) {
        BundledMethodTransform.Builder builder = BundledMethodTransform.builder();
        consumer.accept(builder);
        return new Builder(builder.build());
    }

    public static class Builder {
        private final MethodTransform transform;
        private final List<Supplier<MethodTransform>> onSuccess = new ArrayList<>();
        private final List<Supplier<MethodTransform>> onFail = new ArrayList<>();
        private final List<MethodTransformFilter> filters = new ArrayList<>();

        private Builder(MethodTransform transform) {
            this.transform = transform;
        }

        public Builder onSuccess(Supplier<MethodTransform> onSuccess) {
            this.onSuccess.add(onSuccess);
            return this;
        }

        public Builder onSuccess(Consumer<MethodTransformBuilder<?>> consumer) {
            BundledMethodTransform.Builder builder = BundledMethodTransform.builder();
            consumer.accept(builder);
            this.onSuccess.add(builder::build);
            return this;
        }

        public Builder onFail(Supplier<MethodTransform> onFail) {
            this.onFail.add(onFail);
            return this;
        }

        public Builder onFail(Consumer<MethodTransformBuilder<?>> consumer) {
            BundledMethodTransform.Builder builder = BundledMethodTransform.builder();
            consumer.accept(builder);
            this.onFail.add(builder::build);
            return this;
        }

        public Builder filter(MethodTransformFilter filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder filter(List<MethodTransformFilter> filter) {
            this.filters.addAll(filter);
            return this;
        }

        public MethodTransformationPipeline build() {
            return new MethodTransformationPipeline(this.transform, this.onSuccess, this.onFail, this.filters);
        }

        public Patch.Result apply(MethodContext methodContext) {
            return build().apply(methodContext);
        }
    }
}
