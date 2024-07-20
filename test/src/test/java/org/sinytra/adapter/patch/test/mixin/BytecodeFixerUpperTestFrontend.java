package org.sinytra.adapter.patch.test.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.SimpleTypeAdapter;
import org.sinytra.adapter.patch.fixes.TypeAdapter;

import java.util.List;
import java.util.Map;

public class BytecodeFixerUpperTestFrontend {
    private static final List<TypeAdapter> FIELD_TYPE_ADAPTERS = List.of(
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/world/item/ItemStack"), Type.getObjectType("net/minecraft/world/item/Item"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;")))
    );

    private final BytecodeFixerUpper bfu;

    public BytecodeFixerUpperTestFrontend() {
        this.bfu = new BytecodeFixerUpper(Map.of(), FIELD_TYPE_ADAPTERS);
    }

    public BytecodeFixerUpper unwrap() {
        return this.bfu;
    }
}
