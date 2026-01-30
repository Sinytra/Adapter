package org.sinytra.adapter.patch.config;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.RefMapper;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.analysis.selector.AnnotationValueHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BasePropertyContainer implements MutablePropertyContainer {
    private final Map<PropertyKey<?>, Object> properties = new HashMap<>();
    @Nullable
    protected final PropertyContainerTemplate template;

    public BasePropertyContainer(@Nullable PropertyContainerTemplate template) {
        this.template = template;
    }

    @Override
    public boolean validate() {
        return this.template == null || this.template.validate(this);
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
        if (value != null) {
            this.properties.put(key, value);
        } else {
            this.properties.remove(key);
        }
        return this;
    }

    @Override
    public <T> MutablePropertyContainer removeProperty(PropertyKey<T> key) {
        this.properties.remove(key);
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
    public MutablePropertyContainer mergeFrom(@Nullable PropertyContainer other) {
        if (other != null) {
            other.getProperties().forEach((p, v) -> setProperty((PropertyKey) p, v));
        }
        return this;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void apply(AnnotationHandle handle) {
        this.properties.forEach((key, value) -> {
            Object serialized = ((PropertyKey) key).serialize(value);
            handle.setOrAppendNonNull(key.name(), serialized);
        });
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BasePropertyContainer container = (BasePropertyContainer) o;
        return Objects.equals(properties, container.properties) && Objects.equals(template, container.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties, template);
    }

    protected MutablePropertyContainer createCopyImpl() {
        return new BasePropertyContainer(this.template);
    }

    public static MutablePropertyContainer parseValid(AnnotationHandle handle, @Nullable PropertyContainerTemplate template, RefMapper mapper) {
        MutablePropertyContainer container = parse(handle, template, mapper);
        return container.validate() ? container : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static MutablePropertyContainer parse(AnnotationHandle handle, @Nullable PropertyContainerTemplate template, RefMapper mapper) {
        MutablePropertyContainer container = new BasePropertyContainer(template);

        for (PropertyKey key : template.getKeys()) {
            Object value = handle.getValue(key.name()).map(AnnotationValueHandle::get).orElse(null);
            if (value == null) continue;

            if (key.parser() != null) {
                try {
                    Object parsed = key.parser().parse(value, mapper);
                    container.setProperty(key, parsed);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing property '%s'".formatted(key.name()), e);
                }
            } else {
                throw new IllegalStateException("Cannot parse for key %s, it does not define a parser".formatted(key.name()));
            }
        }

        return container;
    }
}
