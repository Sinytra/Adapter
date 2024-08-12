package org.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.util.function.BiConsumer;

public record SimpleTypeAdapter(Type from, Type to, TypePatch adapter) implements TypeAdapter {
    public interface TypePatch {
        void apply(InsnList list, AbstractInsnNode target);
    }

    @Override
    public void apply(InsnList list, AbstractInsnNode target) {
        this.adapter.apply(list, target);
    }

    @Override
    public TypeAdapter andThen(BiConsumer<InsnList, AbstractInsnNode> consumer) {
        return new SimpleTypeAdapter(this.from, this.to, (l, t) -> {
            consumer.accept(l, t);
            this.adapter.apply(l, t);
        });
    }
}
