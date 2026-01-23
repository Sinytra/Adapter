package org.sinytra.adapter.next.pipeline.config;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.ctx.RefMapper;

import java.util.Objects;

public class PropertyKey<T> {
    private final String name;
    @Nullable
    private final Parser<T> parser;
    @Nullable
    private final Serializer<T> serializer;

    public PropertyKey(String name, @Nullable Parser<T> parser, @Nullable Serializer<T> serializer) {
        this.name = Objects.requireNonNull(name);
        this.parser = parser;
        this.serializer = serializer;
    }

    public String name() {
        return this.name;
    }

    public Parser<T> parser() {
        return this.parser;
    }
    
    public Serializer<T> serializer() {
        return this.serializer;
    }

    public Object serialize(T value) {
        return this.serializer == null ? value : this.serializer.serialize(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }

    public static <T> PropertyKey<T> create(String name) {
        return new PropertyKey<>(name, null, null);
    }

    public static <T> PropertyKey<T> create(String name, Class<T> type) {
        return PropertyKey.<T>builder(name).parseAs(type).build();
    }

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public interface Parser<T> {
        @Nullable
        T parse(Object value, RefMapper mapper);
    }

    public interface Serializer<T> {
        @Nullable
        Object serialize(T value);
    }

    public static class Builder<T> {
        private final String name;
        private Parser<T> parser;
        private Serializer<T> serializer;

        public Builder(String name) {
            this.name = name;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public Builder<T> parseAs(Class<T> type) {
            if (type.isEnum()) {
                return parser((value, mapper) -> {
                    String enumValue = ((String[]) value)[1];
                    return (T) Enum.valueOf((Class) type, enumValue);
                })
                    .serializer(value -> {
                        String name = ((Enum) value).name();
                        return new String[] { type.descriptorString(), name };
                    });
            }
            return parser((value, mapper) -> type.cast(value));
        }

        public Builder<T> parser(Parser<T> parser) {
            this.parser = parser;
            return this;
        }

        public Builder<T> serializer(Serializer<T> serializer) {
            this.serializer = serializer;
            return this;
        }

        public PropertyKey<T> build() {
            return new PropertyKey<>(this.name, this.parser, this.serializer);
        }
    }
}
