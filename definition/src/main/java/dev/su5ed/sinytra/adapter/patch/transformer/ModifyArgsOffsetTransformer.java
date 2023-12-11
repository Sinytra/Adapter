package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
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

    public static void handleModifiedDesc(MethodNode methodNode, String oldQualifier, String newQualifier) {
        String cleanDesc = MethodQualifier.create(oldQualifier).map(MethodQualifier::desc).orElse(null);
        if (cleanDesc == null) {
            return;
        }
        String dirtyDesc = MethodQualifier.create(newQualifier).map(MethodQualifier::desc).orElse(null);
        if (dirtyDesc == null) {
            return;
        }
        Type[] cleanArgs = Type.getArgumentTypes(cleanDesc);
        Type[] dirtyArgs = Type.getArgumentTypes(dirtyDesc);
        // TODO Use EPD
        ParametersDiff diff = ParametersDiff.compareTypeParameters(cleanArgs, dirtyArgs);
        if (!diff.insertions().isEmpty()) {
            modify(methodNode, diff.insertions());
        }
    }

    public static void modify(MethodNode methodNode, List<Pair<Integer, Type>> insertions) {
        ScanningSourceInterpreter i = new ScanningSourceInterpreter(Opcodes.ASM9);
        Analyzer<SourceValue> analyzer = new Analyzer<>(i);

        try {
            analyzer.analyze(methodNode.name, methodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

        for (AbstractInsnNode insn : i.getInsns()) {
            if (insn instanceof IntInsnNode iinsn) {
                insertions.forEach(pair -> {
                    int index = pair.getFirst();
                    if (index >= iinsn.operand) {
                        iinsn.operand += 1;
                    }
                });
            }
            else if (insn instanceof InsnNode iinsn) {
                int index = AdapterUtil.getInsnIntConstValue(iinsn);
                int newIndex = index + (int) insertions.stream().filter(p -> p.getFirst() >= index).count();
                methodNode.instructions.set(insn, AdapterUtil.getIntConstInsn(newIndex));
            }
            else {
                throw new UnsupportedOperationException("Whoopsie! We can't handle " + insn.getClass().getName() + " instructions just yet!");
            }
        }
    }

    public static class ScanningSourceInterpreter extends SourceInterpreter {
        private final List<AbstractInsnNode> insns = new ArrayList<>();
        private final Collection<MethodInsnNode> seen = new HashSet<>();

        public ScanningSourceInterpreter(int api) {
            super(api);
        }

        public List<AbstractInsnNode> getInsns() {
            return this.insns;
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
