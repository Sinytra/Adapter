package org.sinytra.adapter.runtime.inject;

import com.llamalad7.mixinextras.injector.StackExtension;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;

public class ModifyInstanceofValueInjector extends Injector {

    public ModifyInstanceofValueInjector(InjectionInfo info) {
        super(info, "@ModifyInstanceofValue");
    }

    @Override
    protected void inject(Target target, InjectionNodes.InjectionNode node) {
        AbstractInsnNode valueNode = node.getCurrentTarget();
        StackExtension stack = new StackExtension(target);

        InjectorData handler = new InjectorData(target, "instanceof value modifier");
        validateParams(handler, Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE);

        InsnList insns = new InsnList();

        if (!this.isStatic) {
            insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
            insns.add(new InsnNode(Opcodes.SWAP));
        }

        if (handler.captureTargetArgs > 0) {
            pushArgs(target.arguments, insns, target.getArgIndices(), 0, handler.captureTargetArgs);
        }

        stack.receiver(this.isStatic);
        stack.capturedArgs(target.arguments, handler.captureTargetArgs);

        invokeHandler(insns);

        target.insns.insert(valueNode, insns);
    }
}
