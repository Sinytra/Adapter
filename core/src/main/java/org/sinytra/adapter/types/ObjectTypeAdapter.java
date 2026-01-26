package org.sinytra.adapter.types;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.function.BiConsumer;

public class ObjectTypeAdapter implements TypeAdapterProvider {
    public static final TypeAdapterProvider INSTANCE = new ObjectTypeAdapter();

    private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");

    @Override
    public TypeAdapter provide(Type from, Type to) {
        return OBJECT_TYPE.equals(from) && to.getSort() == Type.OBJECT ? new Instance(from, to) : null;
    }

    private record Instance(Type from, Type to) implements TypeAdapter {
        @Override
        public void apply(InsnList list, AbstractInsnNode target) {
            list.insert(target, new TypeInsnNode(Opcodes.CHECKCAST, this.to.getInternalName()));
        }

        @Override
        public TypeAdapter andThen(BiConsumer<InsnList, AbstractInsnNode> consumer) {
            return new SimpleTypeAdapter(this.from, this.to, (l, t) -> {
                consumer.accept(l, t);
                this.apply(l, t);
            });
        }
    }
}
