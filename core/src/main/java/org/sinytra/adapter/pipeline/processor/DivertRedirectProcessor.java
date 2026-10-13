package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.function.Consumer;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;

public class DivertRedirectProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        Consumer<InstructionAdapter> patcher = dirty.getProperty(IntentKeys.DIVERT_REDIRECT).orElse(null);
        if (patcher == null) return TxResult.PASS;

        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE)) return TxResult.PASS;
        MethodQualifier target = dirty.getAtData().getTargetQualifier().orElse(null);

        if (target != null) {
            MethodNode methodNode = context.methodNode();
            boolean applied = false;
            for (MethodInsnNode minsn : MethodAnalyzer.findCalls(methodNode, target)) {
                AbstractInsnNode previousLabel = InsnTraversal.find(minsn, InsnTraversal.BACKWARD, LabelNode.class::isInstance);
                if (previousLabel != null) {
                    MethodNode dummy = new MethodNode();
                    InstructionAdapter adapter = new InstructionAdapter(dummy);

                    LabelNode gotoTarget = new LabelNode();
                    dummy.instructions.add(gotoTarget);

                    patcher.accept(adapter);

                    methodNode.instructions.insert(minsn, dummy.instructions);
                    methodNode.instructions.insert(previousLabel, new JumpInsnNode(Opcodes.GOTO, gotoTarget));
                    applied = true;
                }
            }
            if (applied) {
                return TxResult.SUCCESS;
            }
        }

        return TxResult.PASS;
    }
}
