package org.sinytra.adapter.model;

public sealed interface Relocation {
    record CalledFrom() implements Relocation {
    }

    record ReceivesOrigin(int argIndex) implements Relocation {
    }
}
