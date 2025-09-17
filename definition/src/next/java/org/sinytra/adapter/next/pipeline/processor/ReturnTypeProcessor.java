package org.sinytra.adapter.next.pipeline.processor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.patch.fixes.TypeAdapter;

import java.util.ArrayList;
import java.util.List;

public class ReturnTypeProcessor implements Processor {
    @Override
    public TxResult process(MixinData mixin, MixinContext context, Recipe recipe) {
        Type cleanType = recipe.clean().getReturnType();
        Type dirtyType = recipe.dirty().getReturnType();

        if (cleanType.equals(dirtyType)) {
            return TxResult.PASS;
        }

        TypeAdapter adapter = context.getTypeAdapter(cleanType, dirtyType);
        if (adapter == null) {
            return TxResult.PASS;
        }

        ReturnInterpreter inter = new ReturnInterpreter();
        Analyzer<?> analyzer = new Analyzer<>(inter);
        try {
            analyzer.analyze(context.methodNode().name, context.methodNode());
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

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
            if (value.getSize() != 1) {
                throw new IllegalStateException();
            }
            this.insns.add(value.insns.iterator().next());
        }
    }
}
