package org.sinytra.adapter.patch.test.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.SimpleTypeAdapter;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.List;

public class BytecodeFixerUpperTestFrontend {
    private static final List<TypeAdapter> FIELD_TYPE_ADAPTERS = List.of(
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/world/item/ItemStack"), Type.getObjectType("net/minecraft/world/item/Item"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;"))),
        new SimpleTypeAdapter(
            Type.getObjectType("java/util/function/Consumer"),
            Type.getObjectType("net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
            (list, insn) ->
                list.insert(insn, listOf(
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

    private static InsnList listOf(AbstractInsnNode... nodes) {
        InsnList list = new InsnList();
        for (AbstractInsnNode node : nodes) {
            list.add(node);
        }
        return list;
    }
}
