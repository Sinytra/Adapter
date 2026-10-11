package org.sinytra.adapter.types;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.sinytra.adapter.env.util.TypeConstants;

public class ObjectTypeAdapter implements TypeAdapterProvider {
    public static final TypeAdapterProvider INSTANCE = new ObjectTypeAdapter();

    @Override
    public TypeAdapter provide(Type from, Type to) {
        return TypeConstants.OBJECT_TYPE.equals(from) && to.getSort() == Type.OBJECT ? new Instance(from, to) : null;
    }

    private record Instance(Type from, Type to) implements TypeAdapter {
        @Override
        public void apply(InsnList list, AbstractInsnNode target) {
            list.insert(target, new TypeInsnNode(Opcodes.CHECKCAST, this.to.getInternalName()));
        }
    }
}
