package org.sinytra.adapter.next.pipeline.config;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BasePropertyContainer implements MutablePropertyContainer {
    private final Map<PropertyKey<?>, Object> properties = new HashMap<>();
    @Nullable
    protected final PropertyContainerTemplate template;

    public BasePropertyContainer() {
        this(null);
    }

    public BasePropertyContainer(@Nullable PropertyContainerTemplate template) {
        this.template = template;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public boolean validate() {
        if (this.template != null && !this.template.validate(this)) {
            return false;
        }

        for (Map.Entry<PropertyKey<?>, Object> entry : this.properties.entrySet()) {
            PropertyKey key = entry.getKey();
            if (!key.validate(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasProperty(PropertyKey<?> key) {
        return this.properties.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getProperty(PropertyKey<T> key) {
        T value = (T) this.properties.get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public <T> MutablePropertyContainer setProperty(PropertyKey<T> key, @Nullable T value) {
        this.properties.put(key, value);
        return this;
    }

    @Override
    public Map<PropertyKey<?>, Object> getProperties() {
        return ImmutableMap.copyOf(this.properties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public MutablePropertyContainer copy() {
        MutablePropertyContainer copy = createCopyImpl();
        this.properties.forEach((p, v) -> copy.setProperty((PropertyKey) p, v));
        return copy;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public MutablePropertyContainer mergeFrom(PropertyContainer other) {
        other.getProperties().forEach((p, v) -> setProperty((PropertyKey) p, v));
        return this;
    }

    protected MutablePropertyContainer createCopyImpl() {
        return new BasePropertyContainer();
    }
}
