package dev.su5ed.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public record FieldTypeFix(Type from, Type to, FieldTypePatch typePatch) {
    public interface FieldTypePatch {
        void apply(InsnList list, AbstractInsnNode target);
    }
}
