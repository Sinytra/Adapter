package org.sinytra.adapter.model;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.MethodInsnNode;

public sealed interface Relocation {
    record CalledFrom() implements Relocation {
    }

    record ReceivesOrigin(int argIndex, @Nullable OriginCall via) implements Relocation {
    }

    record OriginCall(TargetPair method, MethodInsnNode call) {
    }
}
