package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.param.Copiable;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

/**
 * Contains a single recipe state with mixin parameters and custom variables
 */
public class ConfigurationImpl extends BasePropertyContainer implements MutableConfiguration {
    @Nullable
    private final Configuration parent;

    public ConfigurationImpl() {
        this(null, null);
    }

    public ConfigurationImpl(@Nullable PropertyContainerTemplate template) {
        this(template, null);
    }

    public ConfigurationImpl(@Nullable PropertyContainerTemplate template, @Nullable Configuration parent) {
        super(template);
        this.parent = parent;
    }

    @Override
    public <T> MutableConfiguration setProperty(PropertyKey<T> key, @Nullable T value) {
        return (MutableConfiguration) super.setProperty(key, value);
    }

    @Override
    public <T> MutableConfiguration removeProperty(PropertyKey<T> key) {
        return (MutableConfiguration) super.removeProperty(key);
    }

    @Override
    public MutableConfiguration inheritMixinType() {
        return inheritProperty(Keys.MIXIN_TYPE);
    }

    @Override
    public MutableConfiguration inheritTargetClass() {
        return inheritProperty(Keys.TARGET_CLASS);
    }

    @Override
    public MutableConfiguration inheritTargetMethod() {
        return inheritProperty(Keys.TARGET_METHOD);
    }

    @Override
    public MutableConfiguration inheritAtData() {
        return inheritProperty(Keys.TARGET_AT);
    }

    @Override
    public MutableConfiguration inheritParameters() {
        return inheritProperty(Keys.PARAMETERS);
    }

    @Override
    public MutableConfiguration inheritReturnType() {
        return inheritProperty(Keys.RETURN_TYPE);
    }
    
    @Override
    public MutableConfiguration inheritShouldDelete() {
        return inheritProperty(Keys.DELETE);
    }

    @Override
    public String getMixinType() {
        return getPropertyOrNull(Keys.MIXIN_TYPE);
    }

    @Override
    public String getTargetClass() {
        return getPropertyOrNull(Keys.TARGET_CLASS);
    }

    @Override
    public MethodQualifier getTargetMethod() {
        return getPropertyOrNull(Keys.TARGET_METHOD);
    }

    @Override
    public AtData getAtData() {
        return getPropertyOrNull(Keys.TARGET_AT);
    }

    @Override
    public MethodParameters getParameters() {
        return getPropertyOrNull(Keys.PARAMETERS);
    }

    @Override
    public Type getReturnType() {
        return getPropertyOrNull(Keys.RETURN_TYPE);
    }

    @Override
    public boolean shouldDelete() {
        Boolean boxed = getProperty(Keys.DELETE).orElse(null);
        return boxed != null && boxed.booleanValue();
    }

    @Override
    public MutableConfiguration setTargetClass(String targetClass) {
        setProperty(Keys.TARGET_CLASS, targetClass);
        return this;
    }

    @Override
    public MutableConfiguration setMixinType(String mixinType) {
        setProperty(Keys.MIXIN_TYPE, mixinType);
        return this;
    }

    @Override
    public MutableConfiguration setTargetMethod(MethodInsnNode insn) {
        setTargetMethod(MethodQualifier.create(insn));
        return this;
    }

    @Override
    public MutableConfiguration setTargetMethod(MethodNode methodNode) {
        setTargetMethod(MethodQualifier.create(methodNode));
        return this;
    }

    @Override
    public MutableConfiguration setTargetMethod(MethodQualifier targetMethod) {
        setProperty(Keys.TARGET_METHOD, targetMethod);
        return this;
    }

    @Override
    public MutableConfiguration setAtData(AtData atData) {
        setProperty(Keys.TARGET_AT, atData);
        return this;
    }

    @Override
    public MutableConfiguration setParameters(MethodParameters parameters) {
        setProperty(Keys.PARAMETERS, parameters);
        return this;
    }

    @Override
    public MutableConfiguration setReturnType(Type returnType) {
        setProperty(Keys.RETURN_TYPE, returnType);
        return this;
    }

    @Override
    public MutableConfiguration setShouldDelete(boolean delete) {
        setProperty(Keys.DELETE, delete);
        return this;
    }

    @Override
    public void inheritProperyIfAbsent(PropertyKey<?> key) {
        if (this.parent == null) {
            throw new IllegalStateException("Missing parent, cannot inherit property " + key);
        }
        if (!hasProperty(key)) {
            inheritProperty(key);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfigurationImpl inheritProperty(PropertyKey<?> key) {
        if (this.parent != null) {
            this.parent.getProperty(key).ifPresent(o -> {
                Object entry = o instanceof Copiable c ? c.copy() : o;
                setProperty((PropertyKey) key, entry);
            });
        }
        return this;
    }

    @Nullable
    private <T> T getPropertyOrNull(PropertyKey<T> key) {
        return getProperty(key).orElse(null);
    }

    @Override
    public MutableConfiguration subConfig() {
        return new ConfigurationImpl(this.template, this.parent);
    }

    @Override
    public MutableConfiguration subConfig(PropertyContainerTemplate template) {
        return new ConfigurationImpl(template, this.parent);
    }

    @Override
    protected MutablePropertyContainer createCopyImpl() {
        return subConfig();
    }

    @Override
    public MutableConfiguration copy() {
        return (MutableConfiguration) super.copy();
    }
}
