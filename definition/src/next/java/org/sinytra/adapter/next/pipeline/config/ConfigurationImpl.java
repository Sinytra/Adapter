package org.sinytra.adapter.next.pipeline.config;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

/**
 * Contains a single recipe state with mixin parameters and custom variables
 */
public class ConfigurationImpl implements MutableConfiguration {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private final Configuration parent;
    private final ConfigAttribute<String> targetClass;
    private final ConfigAttribute<MethodQualifier> targetMethod;
    private final ConfigAttribute<AtData> atData;
    private final ConfigAttribute<MethodParameters> parameters;
    private final ConfigAttribute<Type> returnType;

    private final Map<String, Object> properties = new HashMap<>();

    public ConfigurationImpl() {
        this(null);
    }

    public ConfigurationImpl(@Nullable Configuration parent) {
        this.parent = parent;

        this.targetClass = new ConfigAttribute<>("target_class", true, parent != null ? parent::getTargetClass : null);
        this.targetMethod = new ConfigAttribute<>("target_method", true, parent != null ? parent::getTargetMethod : null);
        this.atData = new ConfigAttribute<>("at_data", true, parent != null ? parent::getAtData : null);
        this.parameters = new ConfigAttribute<>("parameters", true, parent != null ? parent::getParameters : null);
        this.returnType = new ConfigAttribute<>("return_type", true, parent != null ? parent::getReturnType : null);
    }

    public boolean validate() {
        List<ConfigAttribute<?>> attrs = List.of(targetClass, targetMethod, atData, parameters, returnType);
        for (ConfigAttribute<?> attr : attrs) {
            if (!attr.validate()) {
                LOGGER.debug(MIXINPATCH, "Missing required config attribute: {}", attr.getKey());
                return false;
            }
        }
        return true;
    }

    @Override
    public void inheritTargetClass() {
        this.targetClass.setDefault();
    }

    @Override
    public MutableConfiguration inheritTargetMethod() {
        this.targetMethod.setDefault();
        return this;
    }

    @Override
    public MutableConfiguration inheritAtData() {
        this.atData.setDefault();
        return this;
    }

    @Override
    public void inheritParameters() {
        this.parameters.setDefault();
    }

    @Override
    public void inheritReturnType() {
        this.returnType.setDefault();
    }

    @Override
    public String getTargetClass() {
        return this.targetClass.get();
    }

    @Override
    public MethodQualifier getTargetMethod() {
        return this.targetMethod.get();
    }

    @Override
    public AtData getAtData() {
        return this.atData.get();
    }

    @Override
    public MethodParameters getParameters() {
        return this.parameters.get();
    }

    @Override
    public Type getReturnType() {
        return this.returnType.get();
    }

    @Override
    public void setTargetClass(String targetClass) {
        this.targetClass.set(targetClass);
    }

    @Override
    public MutableConfiguration setTargetMethod(MethodQualifier targetMethod) {
        this.targetMethod.set(targetMethod);
        return this;
    }

    @Override
    public MutableConfiguration setTargetMethod(MethodNode methodNode) {
        setTargetMethod(MethodQualifier.create(methodNode));
        return this;
    }

    @Override
    public MutableConfiguration setAtData(AtData atData) {
        this.atData.set(atData);
        return this;
    }

    @Override
    public void setParameters(MethodParameters parameters) {
        this.parameters.set(parameters);
    }

    @Override
    public void setReturnType(Type returnType) {
        this.returnType.set(returnType);
    }

    @Override
    public boolean hasProperty(String key) {
        return this.properties.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getProperty(String key) {
        return Optional.ofNullable((T) this.properties.get(key));
    }

    @Override
    public Map<String, Object> getProperties() {
        return this.properties;
    }

    @Override
    public <T> MutableConfiguration setProperty(String key, T value) {
        this.properties.put(key, value);
        return this;
    }

    @Override
    public MutableConfiguration subConfig() {
        return new ConfigurationImpl(this.parent);
    }

    @Override
    public MutableConfiguration copy() {
        MutableConfiguration copy = new ConfigurationImpl(this.parent);

        copy.setTargetClass(this.targetClass.get());
        copy.setTargetMethod(this.targetMethod.get());
        copy.setAtData(this.atData.get());
        copy.setParameters(this.parameters.get());
        copy.setReturnType(this.returnType.get());
        // TODO Properties

        return copy;
    }

    @Override
    public void mergeFrom(Configuration other) {
        if (other.getTargetClass() != null)
            this.targetClass.set(other.getTargetClass());
        if (other.getTargetMethod() != null)
            this.targetMethod.set(other.getTargetMethod());
        if (other.getAtData() != null)
            this.atData.set(other.getAtData());
        if (other.getParameters() != null)
            this.parameters.set(other.getParameters());
        if (other.getReturnType() != null)
            this.returnType.set(other.getReturnType());

        this.properties.putAll(other.getProperties());
    }
}
