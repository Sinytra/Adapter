package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.serialization.Codec;
import org.objectweb.asm.Type;

public final class ExtraCodecs {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);

    private ExtraCodecs() {}
}
