package org.sinytra.adapter.patch.processor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.SingleValueHandle;

import java.util.List;
import java.util.Objects;

public class StaticAccessProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!recipe.clean().hasProperty(SpecialKeys.STATIC) || !dirty.hasProperty(SpecialKeys.STATIC))
            return TxResult.PASS;

        boolean cleanStatic = recipe.clean().getProperty(SpecialKeys.STATIC).orElseThrow();
        boolean dirtyStatic = dirty.getProperty(SpecialKeys.STATIC).orElseThrow();
        if (cleanStatic == dirtyStatic) return TxResult.PASS;

        MethodNode method = context.methodNode();
        boolean actuallyStatic = MethodHelper.isStatic(context.methodNode());
        // Add static
        if (!actuallyStatic && !cleanStatic && dirtyStatic) {
            // Mixin types add the target instance as the first parameter, which replaces usages of "this"
            Type[] args = Type.getArgumentTypes(method.desc);
            List<Type> targetTypes = context.targetTypes();
            boolean hasInstanceParam = args.length > 0 && targetTypes.size() == 1 && args[0].equals(targetTypes.getFirst());
            boolean usesThis = false;
            for (AbstractInsnNode insn : method.instructions) {
                SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                if (handle != null && handle.get() == 0) {
                    usesThis = true;
                    break;
                }
            }
            if (usesThis && !hasInstanceParam) {
                return TxResult.FAIL;
            }

            context.recordCtxAudit("Made method static");
            method.access |= Opcodes.ACC_STATIC;

            // Remove "this" and shift all other locals down by one slot
            if (method.localVariables != null) {
                method.localVariables.removeIf(lvn -> lvn.index == 0);
                for (LocalVariableNode lvn : method.localVariables) {
                    lvn.index--;
                }
            }
            AdapterUtil.replaceLVT(method, idx -> idx == 0 ? 0 : idx - 1);

            return TxResult.SUCCESS;
        }
        // Remove static
        else if (actuallyStatic && cleanStatic && !dirtyStatic) {
            context.recordCtxAudit("Made method non-static");
            method.access &= ~Opcodes.ACC_STATIC;

            List<LocalVariableNode> localVariables = Objects.requireNonNullElseGet(method.localVariables, List::of);
            LocalVariableNode firstParam = localVariables.stream().filter(lvn -> lvn.index == 0)
                .findFirst()
                .orElse(null);
            // Offset everything by 1
            for (LocalVariableNode lvn : localVariables) {
                lvn.index++;
            }
            AdapterUtil.replaceLVT(method, idx -> idx + 1);

            // Insert instance local variable
            if (firstParam != null) {
                method.localVariables.add(new LocalVariableNode("this", Type.getObjectType(context.classNode().name).getDescriptor(), null, firstParam.start, firstParam.end, 0));
            }

            // TODO Must compute frames
            return TxResult.SUCCESS;
        }

        return TxResult.PASS;
    }
}
