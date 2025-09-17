package org.sinytra.adapter.next.env.ann;

import org.sinytra.adapter.patch.util.MethodQualifier;

public abstract class MixinData {
    private final ClassTarget targetClass;
    private final MethodQualifier targetMethod;
    private final AtData atData;

    public MixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.atData = atData;
    }

    public String getTargetClass() {
        return this.targetClass.getSingle().getInternalName();
    }

    public MethodQualifier getTargetMethod() {
        return this.targetMethod;
    }

    public AtData at() {
        return this.atData;
    }
}
