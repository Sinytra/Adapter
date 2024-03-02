package org.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Type;

public interface TypeAdapterProvider {
    TypeAdapter provide(Type from, Type to);
}
