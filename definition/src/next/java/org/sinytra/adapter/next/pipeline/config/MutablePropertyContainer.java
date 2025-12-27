package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;

public interface MutablePropertyContainer extends PropertyContainer {
    <T> MutablePropertyContainer setProperty(PropertyKey<T> key, @Nullable T value);

    MutablePropertyContainer mergeFrom(PropertyContainer other);
}
