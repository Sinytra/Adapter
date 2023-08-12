package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import org.objectweb.asm.Type;

public final class CodecUtil {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);

    private CodecUtil() {
    }
}
