package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public class PropertyKey<T> {
    private final String name;
    private final Predicate<T> predicate;

    public PropertyKey(String name) {
        this(name, null);
    }

    public PropertyKey(String name, @Nullable Predicate<T> predicate) {
        this.name = Objects.requireNonNull(name);
        this.predicate = Objects.requireNonNullElseGet(predicate, () -> x -> true);
    }

    public boolean validate(T value) {
        return this.predicate.test(value);
    }

    public String name() {
        return this.name;
    }
}
