package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.OptionalInt;

public class ModifyVariableMixinData extends MixinData {
    private final boolean argsOnly;
    private final Integer ordinal;
    
    public ModifyVariableMixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, boolean argsOnly, Integer ordinal) {
        super(targetClass, targetMethod, atData);

        this.argsOnly = argsOnly;
        this.ordinal = ordinal;
    }

    public boolean argsOnly() {
        return this.argsOnly;
    }

    public OptionalInt ordinal() {
        return this.ordinal != null ? OptionalInt.of(this.ordinal) : OptionalInt.empty();
    }
}
