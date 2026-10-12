package org.sinytra.adapter.model;

import org.objectweb.asm.tree.MethodInsnNode;

public sealed interface Relocation {
    record CalledFrom(MethodInsnNode call) implements Relocation {
    }

    record ReceivesOrigin(int argIndex) implements Relocation {
    }
}
