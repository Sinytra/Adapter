package org.sinytra.adapter.model;

import org.objectweb.asm.Type;

public sealed interface ValueSource {
    record Forwarded(int param) implements ValueSource {
    }

    record Captured(Type type, int slot, boolean argsOnly) implements ValueSource {
    }
}
