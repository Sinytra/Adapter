package org.sinytra.adapter.patch.fixes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.function.Supplier;

public class SupplierTypeAdapter implements TypeAdapterProvider {
    public static final TypeAdapterProvider INSTANCE = new SupplierTypeAdapter();

    private static final Type SUPPLIER_TYPE = Type.getType(Supplier.class);
    
    @Override
    public TypeAdapter provide(Type from, Type to) {
        return to.getSort() == Type.OBJECT && SUPPLIER_TYPE.equals(from) ? new Instance(from, to) : null;
    }

    private record Instance(Type from, Type to) implements TypeAdapter {
        @Override
        public void apply(InsnList list, AbstractInsnNode target) {
            InsnList patch = new InsnList();
            patch.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
            patch.add(new TypeInsnNode(Opcodes.CHECKCAST, this.to.getInternalName()));
            list.insert(target, patch);
        }
    }
}
