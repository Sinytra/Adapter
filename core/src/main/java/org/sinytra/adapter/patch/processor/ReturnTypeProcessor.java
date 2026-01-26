package org.sinytra.adapter.patch.processor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.types.TypeAdapter;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;

public class ReturnTypeProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        Type cleanType = recipe.clean().getReturnType();
        Type dirtyType = dirty.getReturnType();

        if (cleanType.equals(dirtyType)) {
            return TxResult.PASS;
        }

        TypeAdapter adapter = context.getTypeAdapter(cleanType, dirtyType);
        if (adapter == null) {
            return TxResult.PASS;
        }

        ReturnInterpreter inter = MethodAnalyzer.analyzeInterpretMethod(context.methodNode(), new ReturnInterpreter());
        for (AbstractInsnNode insn : inter.insns) {
            if (insn.getOpcode() != Opcodes.ACONST_NULL) {
                adapter.apply(context.methodNode().instructions, insn);
            }
        }

        MethodNode methodNode = context.methodNode();
        methodNode.desc = Type.getMethodDescriptor(dirtyType, Type.getArgumentTypes(methodNode.desc));

        return TxResult.SUCCESS;
    }

    private static class ReturnInterpreter extends SourceInterpreter {
        public final List<AbstractInsnNode> insns = new ArrayList<>();

        public ReturnInterpreter() {
            super(Opcodes.ASM9);
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, SourceValue value, SourceValue expected) {
            AbstractInsnNode srcInsn = AdapterUtil.getSingleInsn(value);
            if (srcInsn == null) {
                throw new IllegalStateException();
            }
            this.insns.add(srcInsn);
        }
    }
}
