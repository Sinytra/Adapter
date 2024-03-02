package org.sinytra.adapter.patch.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface SingleValueHandle<T> {
    T get();

    void set(T value);

    static <T> SingleValueHandle<T> of(Supplier<T> getter, Consumer<T> setter) {
        return new SimpleValueHandle<>(getter, setter);
    }

    record SimpleValueHandle<T>(Supplier<T> getter, Consumer<T> setter) implements SingleValueHandle<T> {
        @Override
        public T get() {
            return this.getter.get();
        }

        @Override
        public void set(T value) {
            this.setter.accept(value);
        }
    }
}
