package org.sinytra.adapter.model.config.key;

import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.config.PropertyKey;

public final class IntentKeys {
    /// The mixin moves to the dirty target class
    public static final PropertyKey<Relocation> RELOCATION = PropertyKey.create("relocation");

    private IntentKeys() {
    }
}
