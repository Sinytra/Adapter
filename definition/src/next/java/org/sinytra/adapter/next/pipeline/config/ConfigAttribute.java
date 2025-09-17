package org.sinytra.adapter.next.pipeline.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ConfigAttribute<T> {
    private final String key;
    private final boolean required;

    @Nullable
    private T value;
    @Nullable
    private final Supplier<T> defaultValueSupplier;

    public ConfigAttribute(String key, boolean required, @Nullable Supplier<T> defaultValueSupplier) {
        this.key = key;
        this.required = required;
        this.defaultValueSupplier = defaultValueSupplier;
    }
    
    public String getKey() {
        return this.key;
    }

    public boolean validate() {
        return !this.required || this.value != null;
    }
    
    @Nullable
    public T get() {
        return this.value;
    }

    public void set(@Nullable T value) {
        this.value = value;
    }

    public void setDefault() {
        if (this.defaultValueSupplier != null) {
            set(this.defaultValueSupplier.get());
        } else {
            throw new IllegalStateException("No default value supplier set for %s".formatted(this.key));
        }
    }
}
