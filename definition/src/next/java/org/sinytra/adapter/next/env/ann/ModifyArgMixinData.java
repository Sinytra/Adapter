package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.OptionalInt;

public class ModifyArgMixinData extends MixinData {
    private Integer index;

    public ModifyArgMixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, Integer index) {
        super(targetClass, targetMethod, atData);

        this.index = index;
    }

    public OptionalInt index() {
        return this.index != null ? OptionalInt.of(this.index) : OptionalInt.empty();
    }
}
