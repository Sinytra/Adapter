package org.sinytra.adapter.model.config.key;

import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.config.PropertyKey;

import java.util.function.Consumer;

public final class IntentKeys {
    /// The mixin moves to the dirty target class
    public static final PropertyKey<Relocation> RELOCATION = PropertyKey.create("relocation");

    /// The target method was split, so cancelling in one of them must also apply to the rest
    public static final PropertyKey<Boolean> PROPAGATE_CANCELLATION = PropertyKey.create("propagate_cancellation");

    /// After each redirected call in the handler, run this code instead of the rest of the original statement
    public static final PropertyKey<Consumer<InstructionAdapter>> DIVERT_REDIRECT = PropertyKey.create("divert_redirect");

    private IntentKeys() {
    }
}
