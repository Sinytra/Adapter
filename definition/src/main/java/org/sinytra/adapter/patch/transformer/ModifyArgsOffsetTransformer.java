package org.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public class ModifyArgsOffsetTransformer {
    private static final MethodQualifier ARGS_GET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "get", "(I)Ljava/lang/Object;");
    private static final MethodQualifier ARGS_SET = new MethodQualifier("Lorg/spongepowered/asm/mixin/injection/invoke/arg/Args;", "set", "(ILjava/lang/Object;)V");

    public static void handleModifiedDesc(MethodNode methodNode, String cleanDesc, String dirtyDesc) {
        Type[] cleanArgs = Type.getArgumentTypes(cleanDesc);
        Type[] dirtyArgs = Type.getArgumentTypes(dirtyDesc);
        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(cleanArgs, dirtyArgs);
        if (!diff.insertions().isEmpty()) {
            modify(methodNode, diff.insertions());
        }
    }

    public static void modify(MethodNode methodNode, List<Pair<Integer, Type>> insertions) {
        List<AbstractInsnNode> insns = MethodCallAnalyzer.analyzeMethod(methodNode, (insn, values) -> (ARGS_GET.matches(insn) || ARGS_SET.matches(insn)) && values.size() > 1, (insn, values) -> MethodCallAnalyzer.getSingleInsn(values, 1));
        for (AbstractInsnNode insn : insns) {
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
}
