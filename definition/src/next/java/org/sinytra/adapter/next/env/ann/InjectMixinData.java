package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;
import java.util.Objects;

public class InjectMixinData extends MixinData {
    private final List<SliceData> slice;

    public InjectMixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, List<SliceData> slice) {
        super(targetClass, targetMethod, atData);

        this.slice = Objects.requireNonNull(slice);
    }

    public List<SliceData> getSlice() {
        return this.slice;
    }
}
