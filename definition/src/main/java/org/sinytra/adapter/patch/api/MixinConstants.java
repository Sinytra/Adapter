package org.sinytra.adapter.patch.api;

import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

public class MixinConstants {
    // Standard mixins
    public static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    public static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    public static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    public static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    public static final String MODIFY_VAR = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    public static final String MODIFY_CONST = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    public static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    // Interface mixins
    public static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    public static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    // Mixinextras annotations
    public static final String MODIFY_EXPR_VAL = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    public static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    public static final String WRAP_WITH_CONDITION = "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;";
    public static final String OPERATION_INTERNAL_NAME = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    public static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
    public static final String SHARE = "Lcom/llamalad7/mixinextras/sugar/Share;";
    // Misc
    public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    public static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    public static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    public static final List<Integer> LVT_COMPATIBILITY_LEVELS = List.of(FabricUtil.COMPATIBILITY_0_10_0, FabricUtil.COMPATIBILITY_0_9_2);
}
