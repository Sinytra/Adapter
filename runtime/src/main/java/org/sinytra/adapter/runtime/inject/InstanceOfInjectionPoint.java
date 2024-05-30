package org.sinytra.adapter.runtime.inject;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorByName;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@InjectionPoint.AtCode(value = "INSTANCEOF", namespace = "sinytra")
public class InstanceOfInjectionPoint extends InjectionPoint {
    private final String target;

    public InstanceOfInjectionPoint(InjectionPointData data) {
        super(data);

        this.target = ((ITargetSelectorByName) data.getTarget()).getOwner();
    }

    @Override
    public boolean find(String s, InsnList insns, Collection<AbstractInsnNode> nodes) {
        List<AbstractInsnNode> found = new ArrayList<>();

        for (AbstractInsnNode insn : insns) {
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals(this.target)) {
                found.add(insn);
            }
        }

        nodes.addAll(found);
        return !found.isEmpty();
    }
}
