package org.sinytra.adapter.next.pipeline.config;

import java.util.Map;
import java.util.Optional;

public interface PropertyContainer {
    boolean hasProperty(PropertyKey<?> key);

    <T> Optional<T> getProperty(PropertyKey<T> key);

    Map<PropertyKey<?>, Object> getProperties();

    MutablePropertyContainer copy();

    boolean validate();
}
