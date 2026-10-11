package org.sinytra.adapter.types;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.util.function.BiConsumer;

public interface TypeAdapter {
    Type from();

    Type to();

    void apply(InsnList list, AbstractInsnNode target);

    default TypeAdapter andThen(BiConsumer<InsnList, AbstractInsnNode> consumer) {
        return new SimpleTypeAdapter(from(), to(), (list, target) -> {
            consumer.accept(list, target);
            apply(list, target);
        });
    }
}
