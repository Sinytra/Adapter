package org.sinytra.adapter.patch.config.key;

import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.patch.config.PropertyKey;

import java.util.function.Consumer;

/**
 * Mixin-type-specific Configuration Keys. Never serialized.
 */
public final class SpecialKeys {
    // Hidden
    public static final PropertyKey<MethodInsnNode> EXTRACT_TARGET = PropertyKey.create("_extract_target_minsn");
    public static final PropertyKey<Integer> EXTRACT_ORIGIN_PARAM = PropertyKey.create("_extract_origin_param");
    public static final PropertyKey<Consumer<InstructionAdapter>> REDIRECT_ADAPTER = PropertyKey.create("_redirect_adapter");
    public static final PropertyKey<Boolean> STATIC = PropertyKey.create("_static");

    private SpecialKeys() {
    }
}
