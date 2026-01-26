package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.next.env.ctx.RefMapper;
import org.sinytra.adapter.next.env.util.MixinAnnotations;
import org.sinytra.adapter.next.pipeline.config.MutablePropertyContainer;
import org.sinytra.adapter.next.pipeline.config.PropertyContainer;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.config.PropertyKey;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;
import java.util.Optional;

public class AtData {
    public static final PropertyContainerTemplate TEMPLATE = PropertyContainerTemplate.builder()
        .require(Keys.VALUE)
        .keys(Keys.TARGET, Keys.ORDINAL, Keys.SHIFT, Keys.BY)
        .build();

    private final PropertyContainer properties;

    private AtData(PropertyContainer properties) {
        this.properties = properties;
    }

    public <T> Optional<T> getProperty(PropertyKey<T> key) {
        return this.properties.getProperty(key);
    }

    public String getValue() {
        return getProperty(Keys.VALUE).orElseThrow();
    }

    public Optional<String> getTarget() {
        return getProperty(Keys.TARGET);
    }

    public String getTargetOrThrow() {
        return getTarget().orElseThrow();
    }

    public Optional<Integer> getOrdinal() {
        return getProperty(Keys.ORDINAL);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void apply(AnnotationHandle handle) {
        this.properties.getProperties().forEach((key, value) -> {
            Object serialized = ((PropertyKey) key).serialize(value);
            handle.setOrAppendNonNull(key.name(), serialized);
        });
    }

    public AnnotationNode toAnnotationNode() {
        AnnotationNode node = new AnnotationNode(MixinAnnotations.AT);
        this.properties.getProperties().forEach((key, value) -> node.visit(key.name(), value));
        return node;
    }

    public AtData withTarget(MethodInsnNode insn) {
        return withTarget(MethodQualifier.create(insn));
    }

    public AtData withTarget(MethodQualifier target) {
        return withTarget(target.asDescriptor());
    }

    public AtData withTarget(String target) {
        return withProperty(Keys.TARGET, target);
    }

    public AtData withOrdinal(Integer ordinal) {
        return withProperty(Keys.ORDINAL, ordinal);
    }

    public <T> AtData withProperty(PropertyKey<T> key, T value) {
        MutablePropertyContainer copy = this.properties.copy();
        copy.setProperty(key, value);
        return new AtData(copy);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AtData atData = (AtData) o;
        return Objects.equals(properties, atData.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    public static Optional<AtData> parse(AnnotationHandle annotation, RefMapper mapper) {
        PropertyContainer container = MutablePropertyContainer.parseValid(annotation, TEMPLATE, mapper);
        return Optional.ofNullable(container).map(AtData::new);
    }

    public static AtData create(String value) {
        return builder(value).build();
    }
    
    public static AtData create(String value, MethodInsnNode target) {
        return create(value, MethodQualifier.create(target).asDescriptor());
    }

    public static AtData create(String value, @Nullable String target) {
        Builder builder = builder(value);
        if (value != null) {
            builder.property(Keys.TARGET, target);
        }
        return builder.build();
    }

    public static Builder builder(String value) {
        return new Builder(value);
    }

    public static class Builder {
        private final MutablePropertyContainer properties = MutablePropertyContainer.create(TEMPLATE);

        public Builder(String value) {
            this.properties.setProperty(Keys.VALUE, value);
        }
        
        public Builder target(String target) {
            return property(Keys.TARGET, target);
        }

        public Builder ordinal(Integer ordinal) {
            return property(Keys.ORDINAL, ordinal);
        }

        public <T> Builder property(PropertyKey<T> key, T value) {
            this.properties.setProperty(key, value);
            return this;
        }

        public AtData build() {
            return new AtData(this.properties);
        }
    }

    public static class Keys {
        public static final PropertyKey<String> VALUE = PropertyKey.create("value", String.class);
        public static final PropertyKey<String> TARGET = PropertyKey.<String>builder("target")
            .parser((value, mapper) -> mapper.remap((String) value))
            .build();
        public static final PropertyKey<Integer> ORDINAL = PropertyKey.create("ordinal", Integer.class);
        public static final PropertyKey<At.Shift> SHIFT = PropertyKey.create("shift", At.Shift.class);
        public static final PropertyKey<Integer> BY = PropertyKey.create("by", Integer.class);
    }
}
