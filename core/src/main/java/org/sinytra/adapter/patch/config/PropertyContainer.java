package org.sinytra.adapter.patch.config;

import org.sinytra.adapter.analysis.selector.AnnotationHandle;

import java.util.Map;
import java.util.Optional;

public interface PropertyContainer {
    boolean hasProperty(PropertyKey<?> key);

    <T> Optional<T> getProperty(PropertyKey<T> key);

    Map<PropertyKey<?>, Object> getProperties();

    MutablePropertyContainer copy();

    boolean validate();

    void apply(AnnotationHandle handle);
}
