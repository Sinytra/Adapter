package org.sinytra.adapter.patch.transformer.operation;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.util.MethodTransformBuilderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// TODO Convert into universal operations interface to avoid using raw constructors
public class CompoundMethodTransform implements MethodTransform {
    private final List<MethodTransform> transforms;
    private final List<Supplier<MethodTransform>> onSuccess;
    private final List<Supplier<MethodTransform>> onFail;

    private CompoundMethodTransform(List<MethodTransform> transforms) {
        this(transforms, List.of(), List.of(), List.of());
    }

    private CompoundMethodTransform(List<MethodTransform> transforms, List<Supplier<MethodTransform>> onSuccess, List<Supplier<MethodTransform>> onFail, List<MethodTransformFilter> filters) {
        this.transforms = transforms;
        this.onSuccess = onSuccess;
        this.onFail = onFail;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Patch.Result result = this.transforms.stream()
            .reduce(Patch.Result.PASS, (a, b) -> a.or(b.apply(methodContext)), Patch.Result::or);
        for (Supplier<MethodTransform> supplier : result == Patch.Result.PASS ? this.onFail : this.onSuccess) {
            result = result.or(supplier.get().apply(methodContext));
        }
        return result;
    }

    public static Builder builder(MethodTransform transform) {
        return new Builder(List.of(transform));
    }

    public static Builder builder(List<MethodTransform> transforms) {
        return new Builder(transforms);
    }

    private static class BundleBuilder extends MethodTransformBuilderImpl.ClassImpl<BundleBuilder> {
        private List<MethodTransform> build() {
            return this.transforms;
        }
    }

    public static Builder builder(Consumer<MethodTransformBuilder.Class<?>> consumer) {
        BundleBuilder builder = new BundleBuilder();
        consumer.accept(builder);
        return new Builder(builder.build());
    }

    public static class Builder {
        private final List<MethodTransform> transforms;
        private final List<Supplier<MethodTransform>> onSuccess = new ArrayList<>();
        private final List<Supplier<MethodTransform>> onFail = new ArrayList<>();
        private final List<MethodTransformFilter> filters = new ArrayList<>();

        private Builder(List<MethodTransform> transforms) {
            this.transforms = transforms;
        }

        public Builder onSuccess(Supplier<MethodTransform> onSuccess) {
            this.onSuccess.add(onSuccess);
            return this;
        }

        public Builder onSuccess(Consumer<MethodTransformBuilder<?>> consumer) {
            BundleBuilder builder = new BundleBuilder();
            consumer.accept(builder);
            this.onSuccess.add(() -> new CompoundMethodTransform(builder.build()));
            return this;
        }

        public Builder onFail(Supplier<MethodTransform> onFail) {
            this.onFail.add(onFail);
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

        public CompoundMethodTransform build() {
            return new CompoundMethodTransform(this.transforms, this.onSuccess, this.onFail, this.filters);
        }

        public Patch.Result apply(MethodContext methodContext) {
            return build().apply(methodContext);
        }
    }
}
