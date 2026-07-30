package org.sinytra.adapter.env.util;

import org.spongepowered.asm.mixin.injection.InjectionPoint;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;

public class MixinUtil {
    private static final VarHandle TYPES_HANDLE;
    private static final String CLS_REGEX = "^([A-Za-z_][A-Za-z0-9_]*[\\.\\$])+[A-Za-z_][A-Za-z0-9_]*$";

    static {
        try {
            TYPES_HANDLE = MethodHandles.privateLookupIn(InjectionPoint.class, MethodHandles.lookup())
                .findStaticVarHandle(InjectionPoint.class, "types", Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error unreflecting Injection Point types", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean isKnownInjectionPointType(String value) {
        Map<String, Class<? extends InjectionPoint>> types = (Map<String, Class<? extends InjectionPoint>>) TYPES_HANDLE.get();
        return types.containsKey(value) || value.matches(CLS_REGEX);
    }
}
