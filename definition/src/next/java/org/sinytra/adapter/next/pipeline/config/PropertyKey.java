package org.sinytra.adapter.next.pipeline.config;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.ctx.RefMapper;

import java.util.Objects;
import java.util.function.Predicate;

public class PropertyKey<T> {
    private final String name;
    private final Predicate<T> predicate;
    private final Parser<T> parser;

    public PropertyKey(String name, @Nullable Predicate<T> predicate, @Nullable Parser<T> parser) {
        this.name = Objects.requireNonNull(name);
        this.predicate = Objects.requireNonNullElseGet(predicate, () -> x -> true);
        this.parser = parser;
    }

    public boolean validate(T value) {
        return this.predicate.test(value);
    }

    public String name() {
        return this.name;
    }

    public Parser<T> parser() {
        return this.parser;
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
        T parse(Object value, RefMapper mapper);
    } 

    public static class Builder<T> {
        private final String name;
        private Predicate<T> predicate;
        private Parser<T> parser;

        public Builder(String name) {
            this.name = name;
        }

        public Builder<T> predicate(Predicate<T> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder<T> parseAs(Class<T> type) {
            return parser((value, context) -> type.cast(value));
        }
        
        public Builder<T> parser(Parser<T> parser) {
            this.parser = parser;
            return this;
        }

        public PropertyKey<T> build() {
            return new PropertyKey<>(this.name, this.predicate, this.parser);
        }
    }
}
