package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;

public interface MutablePropertyContainer extends PropertyContainer {
    <T> MutablePropertyContainer setProperty(PropertyKey<T> key, @Nullable T value);
    <T> MutablePropertyContainer removeProperty(PropertyKey<T> key);

    MutablePropertyContainer mergeFrom(PropertyContainer other);
}
