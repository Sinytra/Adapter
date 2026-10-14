package org.sinytra.adapter.model.ann;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.model.config.MutablePropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.PropertyKey;
import org.sinytra.adapter.model.constant.MixinAnnotations;

import java.util.Optional;

public class ConstantData extends AnnotationData {
    private static final PropertyContainerTemplate TEMPLATE = PropertyContainerTemplate.builder()
        .keys(Keys.DOUBLE_VALUE, Keys.CLASS_VALUE, Keys.ORDINAL, Keys.SLICE)
        .build();

    private ConstantData(PropertyContainer properties) {
        super(MixinAnnotations.CONSTANT, properties);
    }

    public Optional<Double> doubleValue() {
        return getProperty(Keys.DOUBLE_VALUE);
    }

    public Optional<Integer> getOrdinal() {
        return getProperty(Keys.ORDINAL);
    }

    public ConstantData withOrdinal(@Nullable Integer ordinal) {
        return withProperty(Keys.ORDINAL, ordinal);
    }

    public <T> ConstantData withProperty(PropertyKey<T> key, @Nullable T value) {
        MutablePropertyContainer copy = this.properties.copy();
        copy.setProperty(key, value);
        return new ConstantData(copy);
    }

    public static ConstantData classValue(Type value) {
        PropertyContainer container = MutablePropertyContainer.create().setProperty(Keys.CLASS_VALUE, value);
        return new ConstantData(container);
    }

    public static Optional<ConstantData> parse(AnnotationHandle handle) {
        MutablePropertyContainer container = MutablePropertyContainer.parseValid(handle, TEMPLATE, s -> s);
        return Optional.ofNullable(container).map(ConstantData::new);
    }

    public static class Keys {
        public static final PropertyKey<Double> DOUBLE_VALUE = PropertyKey.create("doubleValue", Double.class);
        public static final PropertyKey<Type> CLASS_VALUE = PropertyKey.create("classValue", Type.class);
        public static final PropertyKey<Integer> ORDINAL = PropertyKey.create("ordinal", Integer.class);
        public static final PropertyKey<String> SLICE = PropertyKey.create("slice", String.class);
    }
}
