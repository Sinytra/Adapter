package org.sinytra.adapter.analysis.locals;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.env.ctx.LocalVariable;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.transform.param.TransformParameters;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.*;
import java.util.stream.IntStream;

public final class LocalVarAnalyzer {

    public record CapturedLocalsInfo(AdapterUtil.CapturedLocals capturedLocals, ParamsDiffSnapshot diff, List<Type> availableTypes) {
    }

    @Nullable
    public static CapturedLocalsInfo getCapturedLocals(MixinContext context, TargetPair dirtyTarget) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(context, dirtyTarget);
        if (capturedLocals == null) return null;

        // Get available local variables at the injection point in the target method
        List<LocalVariable> available = context.methods().getTargetMethodLocals(capturedLocals.target());
        if (available == null) return null;

        List<Type> availableTypes = available.stream().map(LocalVariable::type).toList();
        // Compare expected and available params
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(capturedLocals.expected(), availableTypes);
        return new CapturedLocalsInfo(capturedLocals, diff, availableTypes);
    }

    public static InsnList findInitializerInsns(MethodNode methodNode, int index) {
        InsnList insns = new InsnList();
        outer:
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                    if (prev instanceof LabelNode) {
                        break outer;
                    }
                    if (prev instanceof FrameNode || prev instanceof LineNumberNode) {
                        continue;
                    }
                    insns.insert(prev.clone(Map.of()));
                }
            }
        }
        return insns;
    }

    public record CapturedLocalsUsage(LocalVariableLookup targetTable, Int2IntMap usageCount, Int2ObjectMap<InsnList> varInsnLists) {
    }

    public record CapturedLocalsTransform(List<Integer> used, TransformParameters remover, Collection<LocalVariableNode> usedLocalNodes) {
        public CapturedLocalsUsage getUsage(AdapterUtil.CapturedLocals capturedLocals) {
            LocalVariableLookup targetTable = new LocalVariableLookup(capturedLocals.target().methodNode());
            Int2ObjectMap<InsnList> varInsnLists = new Int2ObjectOpenHashMap<>();
            Int2IntMap usageCount = new Int2IntOpenHashMap();
            this.used.forEach(ordinal -> {
                int index = targetTable.getByOrdinal(ordinal).index;
                findVariableInitializerInsns(capturedLocals.target().methodNode(), capturedLocals.isStatic(), index, varInsnLists, usageCount);
            });
            return new CapturedLocalsUsage(targetTable, usageCount, varInsnLists);
        }
    }

    public static CapturedLocalsTransform analyzeCapturedLocals(AdapterUtil.CapturedLocals capturedLocals, MethodNode methodNode) {
        // Find used captured locals
        int paramLocalStart = capturedLocals.paramLocalStart();
        LocalVariableLookup table = capturedLocals.lvt();
        List<Integer> used = new ArrayList<>();
        Set<LocalVariableNode> usedLocalNodes = new HashSet<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                LocalVariableNode node = table.getByIndexOrNull(varInsn.var);
                if (node == null) 
                    continue;
                int ordinal = table.getParameterOrdinal(node);
                if (ordinal == -1)
                    continue;
                if (ordinal >= paramLocalStart && ordinal <= capturedLocals.paramLocalEnd()) {
                    used.add(ordinal);
                    usedLocalNodes.add(node);
                }
            }
        }
        // Remove unused captured locals
        TransformParameters remover = TransformParameters.builder()
            .chain(b -> IntStream.range(paramLocalStart, capturedLocals.paramLocalEnd())
                .filter(i -> !used.contains(i))
                .boxed().sorted(Collections.reverseOrder())
                .forEach(b::remove))
            .build();
        return new CapturedLocalsTransform(used, remover, usedLocalNodes);
    }

    public static void findVariableInitializerInsns(MethodNode methodNode, boolean isStatic, int index, Int2ObjectMap<InsnList> varInsnLists, Int2IntMap usageCount) {
        InsnList insns = new InsnList();
        outer:
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                    if (prev instanceof LabelNode) {
                        break outer;
                    }
                    if (prev instanceof FrameNode || prev instanceof LineNumberNode) {
                        continue;
                    }
                    if (prev instanceof VarInsnNode vInsn && (isStatic || vInsn.var != 0) && OpcodeUtil.isLoadOpcode(vInsn.getOpcode())) {
                        // TODO Handle method params
                        if (!varInsnLists.containsKey(vInsn.var)) {
                            findVariableInitializerInsns(methodNode, isStatic, vInsn.var, varInsnLists, usageCount);
                        }
                        usageCount.compute(vInsn.var, (key, existing) -> existing == null ? 1 : existing + 1);
                    }
                    insns.insert(prev.clone(Map.of()));
                }
            }
        }
        varInsnLists.put(index, insns);
    }

    private LocalVarAnalyzer() {
    }
}
