package org.sinytra.adapter.next.types;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.util.function.BiConsumer;

public interface TypeAdapter {
    Type from();

    Type to();

    void apply(InsnList list, AbstractInsnNode target);

    TypeAdapter andThen(BiConsumer<InsnList, AbstractInsnNode> consumer);
}
