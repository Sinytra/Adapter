package org.sinytra.adapter.patch.processor.redirect;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.function.Consumer;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;

public class DivertRedirectProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        Consumer<InstructionAdapter> patcher = dirty.getProperty(SpecialKeys.REDIRECT_ADAPTER).orElse(null);
        if (patcher == null) return TxResult.PASS;

        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE)) return TxResult.PASS;
        MethodQualifier target = dirty.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);

        if (target != null) {
            MethodNode methodNode = context.methodNode();
            boolean applied = false;
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof MethodInsnNode minsn && target.matches(minsn)) {
                    for (AbstractInsnNode previous = insn.getPrevious(); previous != null; previous = previous.getPrevious()) {
                        if (previous instanceof LabelNode) {
                            MethodNode dummy = new MethodNode();
                            InstructionAdapter adapter = new InstructionAdapter(dummy);

                            LabelNode gotoTarget = new LabelNode();
                            dummy.instructions.add(gotoTarget);
                            
                            patcher.accept(adapter);
                            
                            methodNode.instructions.insert(minsn, dummy.instructions);
                            methodNode.instructions.insert(previous, new JumpInsnNode(Opcodes.GOTO, gotoTarget));
                            applied = true;
                            break;
                        }
                    }
                }
            }
            if (applied) {
                return TxResult.SUCCESS;
            }
        }

        return TxResult.PASS;
    }
}
