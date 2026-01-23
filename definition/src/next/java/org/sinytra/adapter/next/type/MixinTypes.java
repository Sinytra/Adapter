package org.sinytra.adapter.next.type;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

public class MixinTypes {
    private static final Map<String, MixinType> MIXIN_TYPES = new HashMap<>();
    
    // TODO:
    // MixinConstants.MODIFY_ARGS, MixinConstants.MODIFY_CONST, MixinConstants.WRAP_WITH_CONDITION,
    // MixinConstants.MODIFY_RETURN_VAL, MixinConstants.OVERWRITE (dont forget special modifyTarget handling)
    // MixinConstants.ACCESSOR
    public static final MixinType INJECT = new InjectMixin();
    public static final MixinType MODIFY_VAR = new ModifyVariableMixin();
    public static final MixinType MODIFY_ARG = new ModifyArgMixin();
    public static final MixinType REDIRECT = new RedirectMixin();
    public static final MixinType WRAP_OP = new WrapOperationMixin();
    public static final MixinType MODIFY_EXPR_VAL = new ModifyExpressionValueMixin();

    static {
        registerMixinType(Inject.class, INJECT);
        registerMixinType(ModifyVariable.class, MODIFY_VAR);
        registerMixinType(ModifyArg.class, MODIFY_ARG);
        registerMixinType(Redirect.class, REDIRECT);
        registerMixinType(MixinConstants.WRAP_OPERATION_INTERNAL_NAME, WRAP_OP);
        registerMixinType(MixinConstants.MODIFY_EXPR_VAL_INTERNAL_NAME, MODIFY_EXPR_VAL);
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
