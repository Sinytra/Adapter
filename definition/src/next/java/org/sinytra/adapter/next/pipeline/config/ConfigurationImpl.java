package org.sinytra.adapter.next.pipeline.config;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
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
    private final ConfigAttribute<String> mixinType;
    private final ConfigAttribute<String> targetClass;
    private final ConfigAttribute<MethodQualifier> targetMethod;
    private final ConfigAttribute<AtData> atData;
    private final ConfigAttribute<MethodParameters> parameters;
    private final ConfigAttribute<Type> returnType;
    private final ConfigAttribute<Boolean> delete;

    private final Map<String, Object> properties = new HashMap<>();

    public ConfigurationImpl() {
        this(null);
    }

    public ConfigurationImpl(@Nullable Configuration parent) {
        this.parent = parent;

        this.mixinType = new ConfigAttribute<>("mixin_type", true, parent != null ? parent::getMixinType : null);
        this.targetClass = new ConfigAttribute<>("target_class", true, parent != null ? parent::getTargetClass : null);
        this.targetMethod = new ConfigAttribute<>("target_method", true, parent != null ? parent::getTargetMethod : null);
        this.atData = new ConfigAttribute<>("at_data", true, parent != null ? parent::getAtData : null);
        this.parameters = new ConfigAttribute<>("parameters", true, parent != null ? parent::getParameters : null);
        this.returnType = new ConfigAttribute<>("return_type", true, parent != null ? parent::getReturnType : null);
        this.delete = new ConfigAttribute<>("delete", false, parent != null ? parent::shouldDelete : () -> false);
    }

    public boolean validate() {
        List<ConfigAttribute<?>> attrs = List.of(mixinType, targetClass, targetMethod, atData, parameters, returnType, delete);
        for (ConfigAttribute<?> attr : attrs) {
            if (!attr.validate()) {
                LOGGER.debug(MIXINPATCH, "Missing required config attribute: {}", attr.getKey());
                return false;
            }
        }
        return true;
    }

    @Override
    public MutableConfiguration inheritMixinType() {
        this.mixinType.setDefault();
        return this;
    }

    @Override
    public MutableConfiguration setMixinType(String mixinType) {
        this.mixinType.set(mixinType);
        return this;
    }

    @Override
    public MutableConfiguration inheritTargetClass() {
        this.targetClass.setDefault();
        return this;
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
    public MutableConfiguration inheritParameters() {
        this.parameters.setDefault();
        return this;
    }

    @Override
    public MutableConfiguration inheritReturnType() {
        this.returnType.setDefault();
        return this;
    }

    @Override
    public String getMixinType() {
        return this.mixinType.get();
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
    public MutableConfiguration setTargetMethod(MethodInsnNode insn) {
        setTargetMethod(MethodQualifier.create(insn));
        return this;
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
    public boolean shouldDelete() {
        Boolean boxed = this.delete.get();
        return boxed != null && boxed.booleanValue();
    }

    @Override
    public MutableConfiguration inheritShouldDelete() {
        this.delete.setDefault();
        return this;
    }

    @Override
    public MutableConfiguration setShouldDelete(boolean delete) {
        this.delete.set(delete);
        return this;
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

    // TODO Remove hardcoding in copy and merge
    @Override
    public MutableConfiguration copy() {
        MutableConfiguration copy = new ConfigurationImpl(this.parent);

        copy.setMixinType(this.mixinType.get());
        copy.setTargetClass(this.targetClass.get());
        copy.setTargetMethod(this.targetMethod.get());
        copy.setAtData(this.atData.get());
        copy.setParameters(this.parameters.get());
        copy.setReturnType(this.returnType.get());
        copy.setShouldDelete(shouldDelete());
        // TODO Properties

        return copy;
    }

    @Override
    public void mergeFrom(Configuration other) {
        if (other.getMixinType() != null)
            this.mixinType.set(other.getMixinType());
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
        if (other.shouldDelete()) {
            this.delete.set(other.shouldDelete());
        }

        this.properties.putAll(other.getProperties());
    }
}
