package org.sinytra.adapter.next.pipeline.processor.wrapop;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.patch.analysis.selector.FrameUtil;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WrapOpAnalyzer {
    public static List<List<AbstractInsnNode>> groupArrayInitializers(MethodNode methodNode, AbstractInsnNode newArrayInsn) {
        // 1. Analyze the method frames to track data flow
        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);

        // Map to hold the AASTORE instruction for each array index
        // Key = Array Index (0, 1...), Value = The AASTORE instruction node
        Map<Integer, AbstractInsnNode> indexToStoreNode = new HashMap<>();

        // 2. Scan for AASTORE instructions targeting our array
        InsnList instructions = methodNode.instructions;
        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);

            if (insn.getOpcode() == Opcodes.AASTORE) {
                Frame<SourceValue> frame = frames[i];

                // Stack for AASTORE: [..., arrayRef, dup, index, value]
                // arrayRef is at stack size - 4
                SourceValue arrayRefSource = frame.getStack(frame.getStackSize() - 4);

                // Check if this AASTORE is operating on the array created by newArrayInsn
                if (arrayRefSource.insns.contains(newArrayInsn)) {
                    // Get the index (stack size - 2)
                    SourceValue indexSource = frame.getStack(frame.getStackSize() - 2);
                    Integer constantIndex = resolveConstantIndex(indexSource);

                    if (constantIndex != null) {
                        indexToStoreNode.put(constantIndex, insn);
                    }
                }
            }
        }

        // 3. Group the instructions linearly
        // We assume the compiler emits instructions in contiguous blocks for each element.
        List<List<AbstractInsnNode>> groups = new ArrayList<>();

        // Start looking after the creation of the array
        AbstractInsnNode currentStart = newArrayInsn.getNext();

        // Find the maximum index we encountered to size our list
        int maxIndex = indexToStoreNode.keySet().stream().max(Integer::compare).orElse(-1);

        for (int i = 0; i <= maxIndex; i++) {
            AbstractInsnNode endNode = indexToStoreNode.get(i);
            List<AbstractInsnNode> currentGroup = new ArrayList<>();

            if (endNode != null) {
                // Collect everything from currentStart up to (and including) the AASTORE
                AbstractInsnNode ptr = currentStart;
                while (ptr != null) {
                    currentGroup.add(ptr);
                    if (ptr == endNode) break;
                    ptr = ptr.getNext();
                }
                // Advance start pointer for the next group
                currentStart = endNode.getNext();
            }

            groups.add(currentGroup);
        }

        return groups;
    }

    // Helper to extract the integer value from a SourceValue
    private static Integer resolveConstantIndex(SourceValue source) {
        for (AbstractInsnNode insn : source.insns) {
            int index = AdapterUtil.getIntConstValue(insn).orElse(-1);
            if (index != -1) {
                return index;
            }
        }
        // Could not resolve to a static constant
        return null;
    }
}
