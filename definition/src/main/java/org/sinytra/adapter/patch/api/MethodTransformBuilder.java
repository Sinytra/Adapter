package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.ApiStatus;
import org.sinytra.adapter.patch.transformer.operation.ModifyInjectionTarget;
import org.sinytra.adapter.patch.transformer.operation.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.operation.ModifyMethodParams;
import org.sinytra.adapter.patch.transformer.operation.ModifyMixinType;
import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;

import java.util.List;
import java.util.function.Consumer;

public interface MethodTransformBuilder<T extends MethodTransformBuilder<T>> {
    @Deprecated
    T modifyParams(Consumer<ModifyMethodParams.Builder> consumer);

    @ApiStatus.Experimental
    T transformParams(Consumer<TransformParameters.Builder> consumer);

    T modifyTarget(String... methods);

    T modifyTarget(ModifyInjectionTarget.Action action, String... methods);

    T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes);

    T extractMixin(String targetClass);

    T improveModifyVar();

    T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer);

    T transform(MethodTransform transformer);

    T transformMethods(List<MethodTransform> transformers);

    T chain(Consumer<T> consumer);
}
