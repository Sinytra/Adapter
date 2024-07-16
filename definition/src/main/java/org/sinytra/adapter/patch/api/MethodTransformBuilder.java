package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.ApiStatus;
import org.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
import org.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.transformer.ModifyMixinType;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;

import java.util.List;
import java.util.function.Consumer;

public interface MethodTransformBuilder<T extends MethodTransformBuilder<T>> {
    @Deprecated
    T modifyParams(Consumer<ModifyMethodParams.Builder> consumer);

    @ApiStatus.Experimental
    T transformParams(Consumer<TransformParameters.Builder> consumer);

    T modifyTarget(String... methods);

    T modifyTarget(ModifyInjectionTarget.Action action, String... methods);

    T modifyVariableIndex(int start, int offset);

    T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes);

    T extractMixin(String targetClass);

    T splitMixin(String targetClass);

    T improveModifyVar();

    T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer);

    T transform(MethodTransform transformer);

    T transformMethods(List<MethodTransform> transformers);

    T chain(Consumer<T> consumer);
}
