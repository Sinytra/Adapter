package org.sinytra.adapter.pipeline.processor.signature;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.LinkedHashSet;
import java.util.Set;

public class ReturnTypeStep implements SignatureStep {
    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        Type cleanType = recipe.clean().getReturnType();
        Type dirtyType = dirty.getReturnType();

        if (cleanType.equals(dirtyType)) {
            return TxResult.PASS;
        }

        TypeAdapter adapter = context.getTypeAdapter(cleanType, dirtyType);
        if (adapter == null) {
            return TxResult.PASS;
        }

        MethodNode methodNode = context.methodNode();
        DataFlow flow = DataFlow.of(methodNode);
        Set<AbstractInsnNode> sources = new LinkedHashSet<>();
        for (AbstractInsnNode ret : MethodAnalyzer.findReturnInsns(methodNode)) {
            flow.operands(ret).forEach(value -> sources.addAll(value.insns));
        }
        for (AbstractInsnNode insn : sources) {
            if (insn.getOpcode() != Opcodes.ACONST_NULL) {
                adapter.apply(methodNode.instructions, insn);
            }
        }

        methodNode.desc = Type.getMethodDescriptor(dirtyType, Type.getArgumentTypes(methodNode.desc));

        return TxResult.SUCCESS;
    }
}
