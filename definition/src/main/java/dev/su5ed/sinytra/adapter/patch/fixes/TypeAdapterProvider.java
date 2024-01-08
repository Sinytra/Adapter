package dev.su5ed.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Type;

public interface TypeAdapterProvider {
    TypeAdapter provide(Type from, Type to);
}
