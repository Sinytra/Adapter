package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.*;

/**
 * Contains a single recipe state with mixin parameters and custom variables
 */
public class ConfigurationImpl implements MutableConfiguration {
    private String targetClass;
    private MethodQualifier targetMethod;
    private AtData atData;

    private MethodParameters parameters;
    private Type returnType;

    private final Map<String, Object> properties = new HashMap<>();

    public ConfigurationImpl(String targetClass, MethodQualifier targetMethod, AtData atData) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.atData = atData;
    }

    public void validate() {
        Objects.requireNonNull(this.targetClass, "targetClass");
        Objects.requireNonNull(this.targetMethod, "targetMethod");
        // TODO Method desc must not be null
        Objects.requireNonNull(this.atData, "atData");
        Objects.requireNonNull(this.parameters, "parameters");
    }

    @Override
    public String getTargetClass() {
        return this.targetClass;
    }

    @Override
    public MethodQualifier getTargetMethod() {
        return this.targetMethod;
    }

    @Override
    public AtData getAtData() {
        return this.atData;
    }

    @Override
    public MethodParameters getParameters() {
        return this.parameters;
    }

    @Override
    public Type getReturnType() {
        return this.returnType;
    }

    @Override
    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public void setTargetMethod(MethodQualifier targetMethod) {
        this.targetMethod = targetMethod;
    }

    @Override
    public void setAtData(AtData atData) {
        this.atData = atData;
    }

    @Override
    public void setParameters(MethodParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getProperty(String key) {
        return Optional.ofNullable((T) this.properties.get(key));
    }

    @Override
    public <T> void setProperty(String key, T value) {
        this.properties.put(key, value);
    }
}
