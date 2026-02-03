package org.sinytra.adapter.patch.config.key;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.config.PropertyKey;

import java.util.List;

/**
 * Mixin Configuration Keys used internally by all mixin types. Never serialized.
 */
@SuppressWarnings("unchecked")
public final class ControlKeys {
    // Mixin meta
    public static final PropertyKey<String> MIXIN_TYPE = PropertyKey.create("mixin_type");
    public static final PropertyKey<String> TARGET_CLASS = PropertyKey.create("target_class");
    // Method
    public static final PropertyKey<MethodParameters> PARAMETERS = PropertyKey.create("parameters");
    public static final PropertyKey<Type> RETURN_TYPE = PropertyKey.create("return_type");
    // Control
    public static final PropertyKey<Boolean> DELETE = PropertyKey.create("delete");

    private ControlKeys() {
    }

    @SuppressWarnings("rawtypes")
    public static <T> T parseSingle(Object obj, Class<T> cls) {
        if (obj instanceof List list) {
            return list.size() == 1 ? (T) list.getFirst() : null;
        }
        return cls.isInstance(obj) ? (T) obj : null;
    }
}
