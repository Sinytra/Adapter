package org.sinytra.adapter.next.env.ann;

import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

public class ConstantData {
    private Object value;

    public ConstantData(Object value) {
        this.value = value;
    }
    
    public static ConstantData classValue(Type value) {
        return new ConstantData(value);
    }

    public void apply(AnnotationHandle handle) {
        if (this.value instanceof Type) {
            handle.setOrAppendNonNull("classValue", this.value);
        } else {
            throw new IllegalStateException("Unexpected value: " + value);
        }
    }
}
