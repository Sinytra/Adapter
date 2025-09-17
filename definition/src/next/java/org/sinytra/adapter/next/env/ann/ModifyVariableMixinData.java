package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.patch.util.MethodQualifier;

public class ModifyVariableMixinData extends MixinData {
    private final boolean argsOnly;
    
    public ModifyVariableMixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, boolean argsOnly) {
        super(targetClass, targetMethod, atData);

        this.argsOnly = argsOnly;
    }

    public boolean argsOnly() {
        return this.argsOnly;
    }
}
