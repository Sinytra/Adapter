package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.util.LocalVariableLookup;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record ModifyDelegatingRedirect(MethodQualifier target) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.REDIRECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        LocalVariableLookup lookup = new LocalVariableLookup(methodNode.localVariables);
        Set<Integer> paramLvtIndices = IntStream.range(1, Type.getArgumentTypes(methodNode.desc).length)
            .map(i -> lookup.getByOrdinal(i).index)
            .boxed()
            .collect(Collectors.toSet());
        List<AbstractInsnNode> insns = MethodCallAnalyzer.analyzeMethod(methodNode, (insn, values) -> this.target.matches(insn) && values.size() > 1, (insn, values) -> MethodCallAnalyzer.getSingleInsn(values, 1));
        if (insns.isEmpty()) {
            return Patch.Result.PASS;
        }
        Set<Integer> loadedLocals = insns.stream()
            .map(i -> i instanceof VarInsnNode varInsn ? varInsn.var : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && paramLvtIndices.contains(varInsn.var) && !loadedLocals.contains(varInsn.var)) {
                return Patch.Result.PASS;
            }
        }
        
        
        
        return Patch.Result.PASS;
    }
}
