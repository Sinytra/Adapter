package org.sinytra.adapter.patch.selector;

import org.objectweb.asm.tree.AnnotationNode;

import java.util.List;
import java.util.Optional;

public class AnnotationValueHandle<T> {
    private List<Object> origin;
    private int index;
    private final String key;
    private boolean invalid;

    public AnnotationValueHandle(List<Object> origin, int index, String key) {
        this.origin = origin;
        this.index = index;
        this.key = key;
    }

    public static <T> Optional<AnnotationValueHandle<T>> create(AnnotationNode node, String key) {
        if (node.values != null) {
            for (int i = 0; i < node.values.size(); i += 2) {
                String atKey = (String) node.values.get(i);
                if (atKey.equals(key)) {
                    int index = i + 1;
                    return Optional.of(new AnnotationValueHandle<>(node.values, index, key));
                }
            }
        }
        return Optional.empty();
    }

    public String getKey() {
        return this.key;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        assertValid();
        return (T) this.origin.get(index);
    }

    public <U> Optional<U> maybeUnwrap() {
        assertValid();
        Object value = get();
        if (value instanceof List<?> list) {
            if (!list.isEmpty()) {
                return Optional.of((U) list.get(0));
            }
            return Optional.empty();
        }
        return Optional.of((U) value);
    }

    public <U> U unwrap() {
        assertValid();
        return this.<U>maybeUnwrap().orElseThrow(() -> new IllegalArgumentException("List '%s' contained no elements".formatted(this.key)));
    }

    public void set(T value) {
        assertValid();
        this.origin.set(index, value);
    }

    public Optional<AnnotationHandle> findNested(String name) {
        assertValid();
        Object value = unwrap();
        if (value instanceof AnnotationNode annotationNode) {
            return create(annotationNode, name).map(h -> h.unwrap() instanceof AnnotationNode ann ? new AnnotationHandle(ann) : null);
        }
        throw new IllegalArgumentException("Expected value to be an AnnotationNode, was " + value.getClass());
    }

    public void refresh(AnnotationNode annotationNode) {
        AnnotationValueHandle.<T>create(annotationNode, this.key).ifPresentOrElse(
            v -> {
                this.origin = v.origin;
                this.index = v.index;
            },
            () -> this.invalid = true
        );
    }

    private void assertValid() {
        if (this.invalid) {
            throw new RuntimeException("Annotation value handle for " + this.key + " is no longer valid.");
        }
    }
}
