package org.sinytra.adapter.patch.mixin;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

public class MixinTypes {
    private static final Map<String, MixinType> MIXIN_TYPES = new HashMap<>();
    
    // TODO:
    public static final MixinType INJECT = new InjectMixin();
    public static final MixinType MODIFY_VAR = new ModifyVariableMixin();
    public static final MixinType MODIFY_ARG = new ModifyArgMixin();
    public static final MixinType MODIFY_ARGS = new ModifyArgsMixin();
    public static final MixinType MODIFY_CONST = new ModifyConstantMixin();
    public static final MixinType MODIFY_RET = new ModifyReturnValueMixin();
    public static final MixinType REDIRECT = new RedirectMixin();
    public static final MixinType WRAP_OP = new WrapOperationMixin();
    public static final MixinType MODIFY_EXPR_VAL = new ModifyExpressionValueMixin();
    public static final MixinType OVERWRITE = new OverwriteMixin();
    public static final MixinType ACCESSOR = new AccessorMixin();
    public static final MixinType INVOKER = new InvokerMixin();

    static {
        registerMixinType(Inject.class, INJECT);
        registerMixinType(ModifyVariable.class, MODIFY_VAR);
        registerMixinType(ModifyArg.class, MODIFY_ARG);
        // MixinConstants.WRAP_WITH_CONDITION is deliberately left out; it appears in none of the corpus mods.
        // The map is keyed by annotation internal names, so the "L...;" descriptor form would never match.
        registerMixinType(MixinAnnotations.MODIFY_ARGS_INTERNAL_NAME, MODIFY_ARGS);
        registerMixinType(MixinAnnotations.MODIFY_CONST_INTERNAL_NAME, MODIFY_CONST);
        registerMixinType(MixinAnnotations.MODIFY_RETURN_VAL_INTERNAL_NAME, MODIFY_RET);
        registerMixinType(Redirect.class, REDIRECT);
        registerMixinType(MixinAnnotations.WRAP_OPERATION_INTERNAL_NAME, WRAP_OP);
        registerMixinType(MixinAnnotations.MODIFY_EXPR_VAL_INTERNAL_NAME, MODIFY_EXPR_VAL);
        registerMixinType(MixinAnnotations.OVERWRITE_INTERNAL_NAME, OVERWRITE);
        registerMixinType(MixinAnnotations.ACCESSOR_INTERNAL_NAME, ACCESSOR);
        registerMixinType(MixinAnnotations.INVOKER_INTERNAL_NAME, INVOKER);
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
