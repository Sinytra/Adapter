package org.sinytra.adapter.types;

import org.objectweb.asm.Type;

public interface TypeAdapterProvider {
    TypeAdapter provide(Type from, Type to);
}
