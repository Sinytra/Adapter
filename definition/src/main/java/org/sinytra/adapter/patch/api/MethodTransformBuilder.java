package org.sinytra.adapter.patch.api;

import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionTarget;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyMixinType;

import java.util.List;
import java.util.function.Consumer;

public interface MethodTransformBuilder<T extends MethodTransformBuilder<T>> {
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

    interface Class<T extends Class<T>> extends MethodTransformBuilder<T> {
        default T modifyInjectionPoint(String value, String target) {
            return modifyInjectionPoint(value, target, false);
        }

        T modifyInjectionPoint(String value, String target, boolean resetValues);

        T modifyInjectionPoint(String value, String target, boolean resetValues, boolean dontUpgrade);

        default T modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }
    }
}
