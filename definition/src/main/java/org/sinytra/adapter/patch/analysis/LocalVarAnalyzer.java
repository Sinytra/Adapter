package org.sinytra.adapter.patch.analysis;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class LocalVarAnalyzer {

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

    public record CapturedLocalsUsage(LocalVariableLookup targetTable, Int2IntMap usageCount, Int2ObjectMap<InsnList> varInsnLists) {}

    public record CapturedLocalsTransform(List<Integer> used, MethodTransform remover) {
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
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                LocalVariableNode node = table.getByIndexOrNull(varInsn.var);
                if (node == null) {
                    continue;
                }
                int ordinal = table.getOrdinal(node);
                if (ordinal >= paramLocalStart && ordinal <= capturedLocals.paramLocalEnd()) {
                    used.add(ordinal - 1); // Subtract 1, which represents the CI param
                }
            }
        }
        // Remove unused captured locals
        MethodTransform remover = TransformParameters.builder()
            .chain(b -> IntStream.range(paramLocalStart, capturedLocals.paramLocalEnd())
                .filter(i -> !used.contains(i))
                .boxed().sorted(Collections.reverseOrder())
                .forEach(b::remove))
            .build();
        return new CapturedLocalsTransform(used, remover);
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

    private LocalVarAnalyzer() {}
}
