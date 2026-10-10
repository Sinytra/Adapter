package org.sinytra.adapter.util;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Cached<T> implements Supplier<T> {
    private static final Object EMPTY = new Object();

    private final Supplier<?> keySupplier;
    private final Function<Object, T> resolver;

    @Nullable
    private Object key = EMPTY;
    @Nullable
    private T value;

    private Cached(Supplier<?> keySupplier, Function<Object, T> resolver) {
        this.keySupplier = keySupplier;
        this.resolver = resolver;
    }

    public static <T> Cached<T> of(Supplier<T> compute) {
        return new Cached<>(() -> null, k -> compute.get());
    }

    @SuppressWarnings("unchecked")
    public static <K, T> Cached<T> keyed(Supplier<K> key, Function<? super K, ? extends T> compute) {
        return new Cached<>(key, k -> compute.apply((K) k));
    }

    @Override
    @Nullable
    public T get() {
        Object currentKey = this.keySupplier.get();
        if (this.key == EMPTY || !Objects.equals(currentKey, this.key)) {
            this.value = this.resolver.apply(currentKey);
            this.key = currentKey;
        }
        return this.value;
    }
}
