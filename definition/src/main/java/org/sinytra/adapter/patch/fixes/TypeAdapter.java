package org.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public interface TypeAdapter {
    Type from();

    Type to();

    void apply(InsnList list, AbstractInsnNode target);
}
