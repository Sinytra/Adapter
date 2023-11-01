package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ModifyArgsOffsetTransformer {
    private static final MethodQualifier ARGS_GET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "get", "(I)Ljava/lang/Object;");
    private static final MethodQualifier ARGS_SET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "set", "(ILjava/lang/Object;)V");

    public static void modify(MethodNode methodNode, List<Pair<Integer, Type>> insertions) {
        List<AbstractInsnNode> insns = new ArrayList<>();
        SourceInterpreter i = new ScanningSourceInterpreter(Opcodes.ASM9, insns);
        Analyzer<SourceValue> analyzer = new Analyzer<>(i);

        try {
            analyzer.analyze(methodNode.name, methodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

        for (AbstractInsnNode insn : insns) {
            if (insn instanceof IntInsnNode iinsn) {
                insertions.forEach(pair -> {
                    int index = pair.getFirst();
                    if (index >= iinsn.operand) {
                        iinsn.operand += 1;
                    }
                });
            } else {
                throw new UnsupportedOperationException("Whoopsie! We can't handle " + insn.getClass().getName() + " instructions just yet!");
            }
        }
    }

    public static class ScanningSourceInterpreter extends SourceInterpreter {
        private final List<AbstractInsnNode> insns;
        private final Collection<MethodInsnNode> seen = new HashSet<>();

        public ScanningSourceInterpreter(int api, List<AbstractInsnNode> insns) {
            super(api);
            this.insns = insns;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode minsn && (ARGS_GET.matches(minsn) || ARGS_SET.matches(minsn)) && values.size() > 1 && !this.seen.contains(minsn)) {
                SourceValue value = values.get(1);
                if (value.insns.size() == 1) {
                    AbstractInsnNode valueInsn = value.insns.iterator().next();
                    this.insns.add(valueInsn);
                    this.seen.add(minsn);
                } else {
                    throw new IllegalStateException("Got multiple source value insns: " + value.insns);
                }
            }
            return super.naryOperation(insn, values);
        }
    }
}
