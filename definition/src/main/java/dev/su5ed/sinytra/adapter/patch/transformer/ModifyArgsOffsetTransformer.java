package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
