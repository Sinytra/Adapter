package org.sinytra.adapter.patch.config;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.RefMapper;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;

public interface MutablePropertyContainer extends PropertyContainer {
    static MutablePropertyContainer create() {
        return create(null);
    }

    static MutablePropertyContainer create(@Nullable PropertyContainerTemplate template) {
        return new BasePropertyContainer(template);
    }

    @Nullable
    static MutablePropertyContainer parseValid(AnnotationHandle handle, @Nullable PropertyContainerTemplate template, RefMapper mapper) {
        return BasePropertyContainer.parseValid(handle, template, mapper);
    }

    static MutablePropertyContainer parse(AnnotationHandle handle, @Nullable PropertyContainerTemplate template, RefMapper mapper) {
        return BasePropertyContainer.parse(handle, template, mapper);
    }

    <T> MutablePropertyContainer setProperty(PropertyKey<T> key, @Nullable T value);

    <T> MutablePropertyContainer removeProperty(PropertyKey<T> key);

    MutablePropertyContainer mergeFrom(@Nullable PropertyContainer other);
}
