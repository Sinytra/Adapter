package org.sinytra.adapter.patch.selector;

import org.objectweb.asm.tree.AnnotationNode;

import java.util.*;

public final class AnnotationHandle {
    private AnnotationNode annotationNode;
    private final Map<String, AnnotationValueHandle<?>> handleCache = new HashMap<>();

    public AnnotationHandle(AnnotationNode annotationNode) {
        this.annotationNode = annotationNode;
    }

    public String getDesc() {
        return this.annotationNode.desc;
    }

    public boolean matchesDesc(String desc) {
        return this.annotationNode.desc.equals(desc);
    }

    public AnnotationNode unwrap() {
        return this.annotationNode;
    }

    public Optional<AnnotationHandle> getNested(String key) {
        return getValue(key)
            .<AnnotationNode>flatMap(AnnotationValueHandle::maybeUnwrap)
            .map(AnnotationHandle::new);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<AnnotationValueHandle<T>> getValue(String key) {
        AnnotationValueHandle<T> handle = (AnnotationValueHandle<T>) this.handleCache.computeIfAbsent(key, k -> AnnotationValueHandle.create(this.annotationNode, key).orElse(null));
        return Optional.ofNullable(handle);
    }

    public void removeValues(String... keys) {
        if (this.annotationNode.values != null) {
            Collection<String> toRemove = Set.of(keys);
            for (int keyIdx = 0; keyIdx < this.annotationNode.values.size(); keyIdx += 2) {
                String atKey = (String) this.annotationNode.values.get(keyIdx);
                if (toRemove.contains(atKey)) {
                    int valueIdx = keyIdx + 1;
                    this.annotationNode.values.remove(valueIdx);
                    this.annotationNode.values.remove(keyIdx);
                    keyIdx -= 2;
                }
            }
            this.handleCache.clear();
        }
    }

    public void appendValue(String key, Object value) {
        if (this.annotationNode.values == null) {
            this.annotationNode.values = new ArrayList<>();
        }
        this.annotationNode.values.add(key);
        this.annotationNode.values.add(value);
        this.handleCache.remove(key);
    }

    public Map<String, AnnotationValueHandle<?>> getAllValues() {
        Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
        if (this.annotationNode.values != null) {
            for (int keyIdx = 0; keyIdx < this.annotationNode.values.size(); keyIdx += 2) {
                String atKey = (String) this.annotationNode.values.get(keyIdx);
                int valueIdx = keyIdx + 1;
                AnnotationValueHandle<?> existing = this.handleCache.compute(atKey, (k, v) -> v != null ? v : new AnnotationValueHandle<>(this.annotationNode.values, valueIdx, atKey));
                map.put(atKey, existing);
            }
        }
        return map;
    }

    public void refresh(AnnotationNode annotationNode) {
        this.annotationNode = annotationNode;
        this.handleCache.values().forEach(v -> v.refresh(annotationNode));
    }
}
