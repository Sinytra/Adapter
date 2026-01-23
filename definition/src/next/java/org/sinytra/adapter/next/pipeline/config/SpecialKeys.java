package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.function.Consumer;

public final class SpecialKeys {
    // Hidden
    public static final PropertyKey<MethodInsnNode> EXTRACT_TARGET = PropertyKey.create("_extract_target_minsn");
    public static final PropertyKey<Consumer<InstructionAdapter>> REDIRECT_ADAPTER = PropertyKey.create("_redirect_adapter");
    public static final PropertyKey<Boolean> STATIC = PropertyKey.create("_static");

    private SpecialKeys() {
    }
}
