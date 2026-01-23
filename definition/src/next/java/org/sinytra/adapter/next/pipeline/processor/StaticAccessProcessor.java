package org.sinytra.adapter.next.pipeline.processor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.SpecialKeys;

public class StaticAccessProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!recipe.clean().hasProperty(SpecialKeys.STATIC) || !dirty.hasProperty(SpecialKeys.STATIC))
            return TxResult.PASS;

        boolean cleanStatic = recipe.clean().getProperty(SpecialKeys.STATIC).orElseThrow();
        boolean dirtyStatic = recipe.dirty().getProperty(SpecialKeys.STATIC).orElseThrow();
        if (cleanStatic == dirtyStatic) return TxResult.PASS;

        MethodNode method = context.methodNode();
        boolean actuallyStatic = MethodHelper.isStatic(context.methodNode());
        // Add static
        if (!actuallyStatic && !cleanStatic && dirtyStatic) {
            // context.recordAudit(this, "Adding access modifier %s", change.modifier);
            method.access |= Opcodes.ACC_STATIC;

            return TxResult.SUCCESS;
        }
        // Remove static
        else if (actuallyStatic && cleanStatic && !dirtyStatic) {
            // context.recordAudit(this, "Removing access modifier %s", change.modifier);
            method.access &= ~Opcodes.ACC_STATIC;

            LocalVariableNode firstParam = method.localVariables.stream().filter(lvn -> lvn.index == 0)
                .findFirst()
                .orElseThrow();
            // Offset everything by 1
            for (LocalVariableNode lvn : method.localVariables) {
                lvn.index++;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof VarInsnNode varInsn) {
                    varInsn.var++;
                }
            }

            // Insert instance local variable
            method.localVariables.add(new LocalVariableNode("this", Type.getObjectType(context.classNode().name).getDescriptor(), null, firstParam.start, firstParam.end, 0));

            // TODO Must compute frames
            return TxResult.SUCCESS;
        }

        return TxResult.PASS;
    }
}
