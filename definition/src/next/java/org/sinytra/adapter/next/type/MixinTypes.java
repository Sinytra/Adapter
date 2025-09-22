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
    private static final Map<String, MixinType<?>> MIXIN_TYPES = new HashMap<>();

    static {
        registerMixinType(Inject.class, new InjectMixin());
        registerMixinType(ModifyVariable.class, new ModifyVariableMixin());
        registerMixinType(ModifyArg.class, new ModifyArgMixin());
        registerMixinType(Redirect.class, new RedirectMixin());
        registerMixinType(MixinConstants.WRAP_OPERATION_INTERNAL_NAME, new WrapOperationMixin());
    }

    @Nullable
    public static MixinType<?> getMixinType(String annotation) {
        return MIXIN_TYPES.get(annotation);
    }

    private static void registerMixinType(Class<?> annotation, MixinType<?> type) {
        String internalName = annotation.getName().replace('.', '/');
        registerMixinType(internalName, type);
    }
    
    private static void registerMixinType(String annInternalName, MixinType<?> type) {
        MIXIN_TYPES.put(annInternalName, type);
    }
}
