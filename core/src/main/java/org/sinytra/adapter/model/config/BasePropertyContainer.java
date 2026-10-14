package org.sinytra.adapter.model.config;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.AnnotationValueHandle;
import org.sinytra.adapter.model.ann.RefMapper;

import java.util.*;

public class BasePropertyContainer implements MutablePropertyContainer {
    private final Map<PropertyKey<?>, Object> properties = new HashMap<>();
    private final List<UnknownValue> unknownValues = new ArrayList<>();
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
        if (copy instanceof BasePropertyContainer base) {
            base.unknownValues.addAll(this.unknownValues);
        }
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
        this.properties.forEach((key, value) -> handle.setOrAppendNonNull(key.name(), serialize((PropertyKey) key, value)));
    }

    @Override
    public <T> Object serialize(PropertyKey<T> key, T value) {
        Object serialized = key.serialize(value);
        boolean plural = this.template != null && this.template.getPluralKeys().contains(key);
        return plural ? List.of(serialized) : serialized;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public AnnotationNode toAnnotationNode(String desc) {
        AnnotationNode node = new AnnotationNode(desc);
        this.properties.forEach((key, value) -> node.visit(key.name(), serialize((PropertyKey) key, value)));
        this.unknownValues.forEach(value -> node.visit(value.name(), value.value()));
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BasePropertyContainer container = (BasePropertyContainer) o;
        return Objects.equals(properties, container.properties) && Objects.equals(unknownValues, container.unknownValues)
            && Objects.equals(template, container.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties, unknownValues, template);
    }

    private record UnknownValue(String name, Object value) {
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
        BasePropertyContainer container = new BasePropertyContainer(template);
        if (template == null) return container;

        Set<String> known = new HashSet<>();
        template.getKeys().forEach(key -> known.add(key.name()));
        List<Object> values = Objects.requireNonNullElse(handle.unwrap().values, List.of());
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (!known.contains((String) values.get(i))) {
                container.unknownValues.add(new UnknownValue((String) values.get(i), values.get(i + 1)));
            }
        }

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
