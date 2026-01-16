package org.sinytra.adapter.patch.util;

import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MethodTransformBuilder;
import org.sinytra.adapter.patch.transformer.ModifyVarUpgradeToModifyExprVal;
import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;
import org.sinytra.adapter.patch.transformer.operation.unit.ExtractMixin;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionTarget;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyMixinType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MethodTransformBuilderImpl<T extends MethodTransformBuilder<T>> implements MethodTransformBuilder<T> {
    protected final List<MethodTransform> transforms = new ArrayList<>();

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
}
