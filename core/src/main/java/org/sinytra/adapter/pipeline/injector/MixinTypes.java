package org.sinytra.adapter.pipeline.injector;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

public class MixinTypes {
    private static final Map<String, MixinType> MIXIN_TYPES = new HashMap<>();
    
    // TODO:
    // MixinConstants.MODIFY_ARGS, MixinConstants.MODIFY_CONST,
    // MixinConstants.OVERWRITE (dont forget special modifyTarget handling)
    // MixinConstants.ACCESSOR
    public static final MixinType INJECT = new InjectMixin();
    public static final MixinType MODIFY_VAR = new ModifyVariableMixin();
    public static final MixinType MODIFY_ARG = new ModifyArgMixin();
    public static final MixinType MODIFY_RET = new ModifyReturnValueMixin();
    public static final MixinType REDIRECT = new RedirectMixin();
    public static final MixinType WRAP_OP = new WrapOperationMixin();
    public static final MixinType MODIFY_EXPR_VAL = new ModifyExpressionValueMixin();
    public static final MixinType WRAP_WITH_CONDITION = new WrapWithConditionMixin();

    static {
        registerMixinType(Inject.class, INJECT);
        registerMixinType(ModifyVariable.class, MODIFY_VAR);
        registerMixinType(ModifyArg.class, MODIFY_ARG);
        registerMixinType(MixinAnnotations.MODIFY_RETURN_VAL_INTERNAL_NAME, MODIFY_RET);
        registerMixinType(Redirect.class, REDIRECT);
        registerMixinType(MixinAnnotations.WRAP_OPERATION_INTERNAL_NAME, WRAP_OP);
        registerMixinType(MixinAnnotations.MODIFY_EXPR_VAL_INTERNAL_NAME, MODIFY_EXPR_VAL);
        registerMixinType(MixinAnnotations.WRAP_WITH_CONDITION_INTERNAL_NAME, WRAP_WITH_CONDITION);
        registerMixinType(MixinAnnotations.WRAP_WITH_CONDITION_V2_INTERNAL_NAME, WRAP_WITH_CONDITION);
    }

    @Nullable
    public static MixinType getMixinType(String annotation) {
        return MIXIN_TYPES.get(annotation);
    }

    private static void registerMixinType(Class<?> annotation, MixinType type) {
        String internalName = annotation.getName().replace('.', '/');
        registerMixinType(internalName, type);
    }
    
    private static void registerMixinType(String annInternalName, MixinType type) {
        MIXIN_TYPES.put(annInternalName, type);
    }
}
