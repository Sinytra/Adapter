package dev.su5ed.sinytra.adapter.patch;

import org.objectweb.asm.tree.AnnotationNode;

import java.util.List;
import java.util.Optional;

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

    public Optional<AnnotationValueHandle<AnnotationNode>> findNested(String name) {
        Object value = get();
        if (value instanceof List<?> list && list.size() == 1) {
            value = list.get(0);
        }
        if (value instanceof AnnotationNode annotationNode) {
            return PatchInstance.findAnnotationValue(annotationNode.values, name);
        }
        throw new IllegalArgumentException("Expected value to be an AnnotationNode, was " + value.getClass());
    }
}
