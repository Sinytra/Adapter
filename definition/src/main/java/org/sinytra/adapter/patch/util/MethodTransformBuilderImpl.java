package org.sinytra.adapter.patch.util;

import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MethodTransformBuilder;
import org.sinytra.adapter.patch.transformer.ModifyVarUpgradeToModifyExprVal;
import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;
import org.sinytra.adapter.patch.transformer.operation.unit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MethodTransformBuilderImpl<T extends MethodTransformBuilder<T>> implements MethodTransformBuilder<T> {
    protected final List<MethodTransform> transforms = new ArrayList<>();

    @Override
    public T modifyParams(Consumer<ModifyMethodParams.Builder> consumer) {
        ModifyMethodParams.Builder builder = ModifyMethodParams.builder();
        consumer.accept(builder);
        return transform(builder.build());
    }

    @Override
    public T transformParams(Consumer<TransformParameters.Builder> consumer) {
        TransformParameters.Builder builder = new TransformParameters.Builder();
        consumer.accept(builder);
        return transform(builder.build());
    }

    @Override
    public T modifyTarget(String... methods) {
        return transform(new ModifyInjectionTarget(List.of(methods)));
    }

    @Override
    public T modifyTarget(ModifyInjectionTarget.Action action, String... methods) {
        return transform(new ModifyInjectionTarget(List.of(methods), action));
    }

    @Override
    public T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes) {
        return transform(new ModifyMethodAccess(List.of(changes)));
    }

    @Override
    public T extractMixin(String targetClass) {
        return improveModifyVar()
            .transform(new ExtractMixin(targetClass));
    }

    @Override
    public T improveModifyVar() {
        return transform(ModifyVarUpgradeToModifyExprVal.INSTANCE);
    }

    @Override
    public T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer) {
        return transform(new ModifyMixinType(newType, consumer));
    }

    @Override
    public T transform(MethodTransform transformer) {
        this.transforms.add(transformer);
        return coerce();
    }

    @Override
    public T transformMethods(List<MethodTransform> transformers) {
        transformers.forEach(this::transform);
        return coerce();
    }

    @Override
    public T chain(Consumer<T> consumer) {
        consumer.accept(coerce());
        return coerce();
    }

    @SuppressWarnings("unchecked")
    private T coerce() {
        return (T) this;
    }

    public static class ClassImpl<T extends MethodTransformBuilder.Class<T>> extends MethodTransformBuilderImpl<T> implements MethodTransformBuilder.Class<T> {
        @Override
        public T modifyInjectionPoint(String value, String target, boolean resetValues) {
            return modifyInjectionPoint(value, target, resetValues, false);
        }

        @Override
        public T modifyInjectionPoint(String value, String target, boolean resetValues, boolean dontUpgrade) {
            return transform(new ModifyInjectionPoint(value, target, resetValues, dontUpgrade));
        }
    }
}
