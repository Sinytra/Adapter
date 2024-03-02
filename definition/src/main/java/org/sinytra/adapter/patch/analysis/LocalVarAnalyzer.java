package org.sinytra.adapter.patch.analysis;

import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.Map;

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

    private LocalVarAnalyzer() {}
}
