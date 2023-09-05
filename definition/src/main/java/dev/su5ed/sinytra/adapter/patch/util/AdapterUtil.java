package dev.su5ed.sinytra.adapter.patch.util;

import org.objectweb.asm.Type;

public final class AdapterUtil {

    public static int getLVTOffsetForType(Type type) {
        return type.equals(Type.DOUBLE_TYPE) || type.equals(Type.LONG_TYPE) ? 2 : 1;
    }
    
    private AdapterUtil() {}
}
