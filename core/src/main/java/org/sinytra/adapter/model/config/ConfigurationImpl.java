package org.sinytra.adapter.model.config;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.param.Copiable;

/**
 * Contains a single recipe state with mixin parameters and custom variables
 */
public class ConfigurationImpl extends BasePropertyContainer implements MutableConfiguration {
    @Nullable
    private final Configuration parent;

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
    public MutableConfiguration inheritProperyIfAbsent(PropertyKey<?> key) {
        if (this.parent == null) {
            throw new IllegalStateException("Missing parent, cannot inherit property " + key);
        }
        if (!hasProperty(key)) {
            inheritProperty(key);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public MutableConfiguration inheritProperty(PropertyKey<?> key) {
        if (this.parent != null) {
            this.parent.getProperty(key).ifPresent(o -> {
                Object entry = o instanceof Copiable c ? c.copy() : o;
                setProperty((PropertyKey) key, entry);
            });
        }
        return this;
    }

    @Override
    public MutableConfiguration copyClean() {
        return new ConfigurationImpl(this.template, this.parent);
    }

    @Override
    public MutableConfiguration copyClean(PropertyContainerTemplate template) {
        return new ConfigurationImpl(template, this.parent);
    }

    @Override
    protected MutablePropertyContainer createCopyImpl() {
        return copyClean();
    }

    @Override
    public MutableConfiguration copy() {
        return (MutableConfiguration) super.copy();
    }

    @Override
    public MutableConfiguration childConfig() {
        return new ConfigurationImpl(this.template, this);
    }

    @Override
    public MutableConfiguration mergeFrom(@Nullable PropertyContainer other) {
        super.mergeFrom(other);
        return this;
    }
}
