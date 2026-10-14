package org.sinytra.adapter.model.ann;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.model.config.MutablePropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.PropertyKey;
import org.sinytra.adapter.model.constant.MixinAnnotations;

public class SliceData extends AnnotationData {
    private static final PropertyContainerTemplate TEMPLATE = PropertyContainerTemplate.builder()
        .keys(Keys.FROM, Keys.TO)
        .build();

    private SliceData(PropertyContainer properties) {
        super(MixinAnnotations.SLICE, properties);
    }

    @Nullable
    public AtData from() {
        return getProperty(Keys.FROM).orElse(null);
    }

    @Nullable
    public AtData to() {
        return getProperty(Keys.TO).orElse(null);
    }

    public SliceData withBoundaries(@Nullable AtData from, @Nullable AtData to) {
        MutablePropertyContainer copy = this.properties.copy();
        copy.setProperty(Keys.FROM, from);
        copy.setProperty(Keys.TO, to);
        return new SliceData(copy);
    }

    public static SliceData empty() {
        return new SliceData(MutablePropertyContainer.create(TEMPLATE));
    }

    public static SliceData parse(AnnotationHandle handle, RefMapper mapper) {
        return new SliceData(MutablePropertyContainer.parse(handle, TEMPLATE, mapper));
    }

    public static class Keys {
        public static final PropertyKey<AtData> FROM = boundary("from");
        public static final PropertyKey<AtData> TO = boundary("to");

        private static PropertyKey<AtData> boundary(String name) {
            return PropertyKey.<AtData>builder(name)
                .parser((value, mapper) -> AtData.parse(new AnnotationHandle((AnnotationNode) value), mapper).orElse(null))
                .serializer(AtData::toAnnotationNode)
                .build();
        }
    }
}
