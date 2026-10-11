package org.sinytra.adapter.types;

import org.objectweb.asm.Type;
import org.sinytra.adapter.api.TypeAdapter;

public interface TypeAdapterProvider {
    TypeAdapter provide(Type from, Type to);
}
