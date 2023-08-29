package dev.su5ed.sinytra.adapter.patch;

import java.util.List;

public class AnnotationValueHandle<T> {
    private final List<Object> origin;
    private final int index;
    private final String key;

    public AnnotationValueHandle(List<Object> origin, int index, String key) {
        this.origin = origin;
        this.index = index;
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        return (T) this.origin.get(index);
    }

    public void set(T value) {
        this.origin.set(index, value);
    }
}
