package org.sinytra.adapter.model.config;

import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.model.ann.AnnotationHandle;

import java.util.Map;
import java.util.Optional;

public interface PropertyContainer {
    boolean hasProperty(PropertyKey<?> key);

    <T> Optional<T> getProperty(PropertyKey<T> key);

    Map<PropertyKey<?>, Object> getProperties();

    MutablePropertyContainer copy();

    boolean validate();

    void apply(AnnotationHandle handle);

    <T> Object serialize(PropertyKey<T> key, T value);

    AnnotationNode toAnnotationNode(String desc);
}
