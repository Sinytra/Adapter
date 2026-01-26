package org.sinytra.adapter.patch.test.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.sinytra.adapter.next.types.BytecodeFixerUpper;
import org.sinytra.adapter.next.types.SimpleTypeAdapter;
import org.sinytra.adapter.next.types.TypeAdapter;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.List;

import static org.sinytra.adapter.patch.util.AdapterUtil.insnList;

public class BytecodeFixerUpperTestFrontend {
    private static final List<TypeAdapter> FIELD_TYPE_ADAPTERS = List.of(
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/world/item/ItemStack"), Type.getObjectType("net/minecraft/world/item/Item"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;"))),
        new SimpleTypeAdapter(
            Type.getObjectType("java/util/function/Consumer"),
            Type.getObjectType("net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
            (list, insn) ->
                list.insert(insn, insnList(
                    new TypeInsnNode(Opcodes.NEW, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
                    new InsnNode(Opcodes.DUP),
                    new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor", "<init>", "(Ljava/util/function/Consumer;)V")
                ))),
        new SimpleTypeAdapter(
            Type.getObjectType("net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
            Type.getObjectType("java/util/function/Consumer"),
            (list, insn) ->
                list.insert(insn, new FieldInsnNode(Opcodes.GETFIELD, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor", "consumer", "Ljava/util/function/Consumer;")))
    );

    private final BytecodeFixerUpper bfu;

    public BytecodeFixerUpperTestFrontend(ClassLookup cleanLookup, ClassLookup dirtyLookup) {
        this.bfu = new BytecodeFixerUpper(cleanLookup, dirtyLookup, FIELD_TYPE_ADAPTERS);
    }

    public BytecodeFixerUpper unwrap() {
        return this.bfu;
    }
}
