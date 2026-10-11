package org.sinytra.adapter.env.ann;

import com.google.common.base.MoreObjects;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyKey;

import java.util.Objects;
import java.util.Optional;

public abstract class AnnotationData {
    private final String desc;
    protected final PropertyContainer properties;

    protected AnnotationData(String desc, PropertyContainer properties) {
        this.desc = desc;
        this.properties = properties;
    }

    public <T> Optional<T> getProperty(PropertyKey<T> key) {
        return this.properties.getProperty(key);
    }

    public AnnotationNode toAnnotationNode() {
        return this.properties.toAnnotationNode(this.desc);
    }

    @Override
    public boolean equals(Object o) {
        return o != null && getClass() == o.getClass() && Objects.equals(this.properties, ((AnnotationData) o).properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.properties);
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        this.properties.getProperties().forEach((k, v) -> helper.add(k.name(), v));
        return helper.toString();
    }
}
