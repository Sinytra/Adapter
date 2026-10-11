package org.sinytra.adapter.model.config;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.MethodParameters;

public interface Configuration extends PropertyContainer {
    @Nullable
    default String getMixinType() {
        return getProperty(ControlKeys.MIXIN_TYPE).orElse(null);
    }

    @Nullable
    default String getTargetClass() {
        return getProperty(ControlKeys.TARGET_CLASS).orElse(null);
    }

    @Nullable
    default MethodQualifier getTargetMethod() {
        return getProperty(MixinKeys.TARGET_METHOD).orElse(null);
    }

    @Nullable
    default AtData getAtData() {
        return getProperty(MixinKeys.TARGET_AT).orElse(null);
    }

    @Nullable
    default MethodParameters getParameters() {
        return getProperty(ControlKeys.PARAMETERS).orElse(null);
    }

    @Nullable
    default Type getReturnType() {
        return getProperty(ControlKeys.RETURN_TYPE).orElse(null);
    }

    default boolean shouldDelete() {
        return getProperty(ControlKeys.DELETE).orElse(false);
    }

    default boolean isCancellable() {
        return getProperty(MixinKeys.CANCELLABLE).orElse(false);
    }

    MutableConfiguration childConfig();

    MutableConfiguration copyClean();

    MutableConfiguration copyClean(PropertyContainerTemplate template);

    MutableConfiguration copy();
}
