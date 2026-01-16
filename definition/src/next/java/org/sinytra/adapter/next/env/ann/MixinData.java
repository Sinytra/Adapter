package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainer;
import org.sinytra.adapter.next.pipeline.config.PropertyKey;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Optional;

public class MixinData {
    private final ClassTarget targetClass;
    private final MethodQualifier targetMethod;
    private final AtData atData;
    private final PropertyContainer properties;

    public MixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, PropertyContainer properties) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.atData = atData;
        this.properties = properties;
    }

    public String getTargetClass() {
        return this.targetClass.getSingle().getInternalName();
    }

    public MethodQualifier getTargetMethod() {
        return this.targetMethod;
    }

    public AtData at() {
        return this.atData;
    }

    public <T> Optional<T> getProperty(PropertyKey<T> key) {
        return this.properties.getProperty(key);
    }

    public boolean isCancellable() {
        return getProperty(Configuration.Keys.CANCELLABLE).orElse(false);
    }
}
