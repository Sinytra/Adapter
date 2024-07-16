package org.sinytra.adapter.patch.util;

import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MethodTransformBuilder;
import org.sinytra.adapter.patch.transformer.*;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;

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
    public T modifyVariableIndex(int start, int offset) {
        return transform(new ChangeModifiedVariableIndex(start, offset));
    }

    @Override
    public T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes) {
        return transform(new ModifyMethodAccess(List.of(changes)));
    }

    @Override
    public T extractMixin(String targetClass) {
        return transform(ModifyVarUpgradeToModifyExprVal.INSTANCE)
            .transform(new ExtractMixin(targetClass));
    }

    @Override
    public T splitMixin(String targetClass) {
        return transform(new SplitMixinTransform(targetClass));
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
