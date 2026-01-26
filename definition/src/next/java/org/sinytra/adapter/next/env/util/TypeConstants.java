package org.sinytra.adapter.next.env.util;

import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class TypeConstants {
    public static final String OPERATION_INTERNAL_NAME = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    public static final Type OPERATION_TYPE = Type.getObjectType(OPERATION_INTERNAL_NAME);

    public static final Type CI_TYPE = Type.getType(CallbackInfo.class);
    public static final Type CIR_TYPE = Type.getType(CallbackInfoReturnable.class);

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final String DEPRECATED = Type.getType(Deprecated.class).getDescriptor();
}
