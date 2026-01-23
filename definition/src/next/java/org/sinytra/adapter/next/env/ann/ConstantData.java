package org.sinytra.adapter.next.env.ann;

import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

// TODO Cleanup
public class ConstantData {
    private final Object value;

    private ConstantData(Object value) {
        this.value = value;
    }

    public Optional<Type> classValue() {
        return value instanceof Type t ? Optional.of(t) : Optional.empty();
    }

    public OptionalDouble doubleValue() {
        return value instanceof Double d ? OptionalDouble.of(d) : OptionalDouble.empty();
    }

    public static ConstantData classValue(Type value) {
        return new ConstantData(value);
    }

    public void apply(AnnotationHandle handle) {
        if (this.value instanceof Type) {
            handle.setOrAppendNonNull("classValue", this.value);
        } else if (this.value instanceof Double) {
            handle.setOrAppendNonNull("doubleValue", this.value);
        } else {
            throw new IllegalStateException("Unexpected value: " + value);
        }
    }

    public static Optional<ConstantData> parse(AnnotationHandle handle) {
        List<String> knownKeys = List.of("doubleValue", "classValue");
        for (String key : knownKeys) {
            Object value = handle.getValue(key).map(AnnotationValueHandle::get).orElse(null);
            if (value != null) {
                return Optional.of(new ConstantData(value));
            }
        }
        return Optional.empty();
    }
}
