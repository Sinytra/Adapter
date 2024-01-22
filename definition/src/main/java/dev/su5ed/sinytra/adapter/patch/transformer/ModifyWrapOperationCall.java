package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

public record ModifyWrapOperationCall(List<Pair<Integer, Integer>> insertions) implements MethodTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        if (!annotation.matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return Patch.Result.PASS;
        }

        var wrapOpIndex = -1;
        for (int i = 0; i < params.length; i++) {
            if (params[i].getInternalName().equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")) {
                wrapOpIndex = i;
                break;
            }
        }

        if (wrapOpIndex < 0) {
            return Patch.Result.PASS;
        }
        boolean isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;
        if (isNonStatic) wrapOpIndex++;

        // A lot of assumptions ahead, beware. Here be dragons
        for (int i = 0; i < methodNode.instructions.size(); i++) {
            var insn = methodNode.instructions.get(i);
            if (insn instanceof MethodInsnNode minsn && minsn.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") && minsn.name.equals("call")) {
                // Backtrace until we find the load instruction of the operation var
                int loadInsnIndex = -1;
                for (int j = i - 1; j >= 0; j--) {
                    if (methodNode.instructions.get(i) instanceof VarInsnNode vin && vin.var == wrapOpIndex && vin.getOpcode() == Opcodes.ALOAD) {
                        loadInsnIndex = j;
                        break;
                    }
                }
                if (loadInsnIndex == -1) {
                    return Patch.Result.PASS;
                }

                // Assume that people are only calling `call` on the operation object
                if (!(
                        AdapterUtil.getIntConstValue(methodNode.instructions.get(loadInsnIndex + 1)).isPresent() &&
                        methodNode.instructions.get(loadInsnIndex + 2) instanceof TypeInsnNode n && n.getOpcode() == Opcodes.ANEWARRAY
                )) {
                    return Patch.Result.PASS;
                }

                final int oldArraySize = AdapterUtil.getIntConstValue(methodNode.instructions.get(loadInsnIndex + 1)).getAsInt();
                int newArrayLength = oldArraySize + insertions.size();

                methodNode.instructions.set(methodNode.instructions.get(loadInsnIndex + 1), new IntInsnNode(Opcodes.BIPUSH, newArrayLength));

                List<AbstractInsnNode>[] objects = new List[newArrayLength];
                for (int j = 0; j < newArrayLength; j++) objects[j] = new ArrayList<>();

                // Find sequences of "PUSH/<CODE>/AASTORE/DUP" which represent the values of the vararg array
                int currentSequenceIndex = -1;
                for (int j = loadInsnIndex + 1; j < i; j++) {
                    final var instruction = methodNode.instructions.get(j);
                    final var possibleIdx = AdapterUtil.getIntConstValue(instruction);
                    if (currentSequenceIndex == -1 && possibleIdx.isPresent()) {
                        currentSequenceIndex = possibleIdx.getAsInt();
                        continue;
                    }
                    if (currentSequenceIndex != -1) {
                        if (instruction.getOpcode() == Opcodes.AASTORE && methodNode.instructions.get(j + 1).getOpcode() == Opcodes.DUP) {
                            j++;
                            currentSequenceIndex = -1;
                            continue;
                        }

                        objects[currentSequenceIndex].add(instruction);
                    }
                }

                insertions.forEach(insertion -> {
                    final var position = insertion.getFirst();
                    final var target = insertion.getSecond();

                    final var node = new IntInsnNode(Opcodes.ALOAD, target);
                    if (!objects[position].isEmpty()) {
                        for (int j = newArrayLength - 1; j >= position; j--) {
                            objects[j] = objects[j - 1];
                            objects[j - 1] = new ArrayList<>();
                            if (!objects[j].isEmpty()) {
                                methodNode.instructions.set(methodNode.instructions.get(methodNode.instructions.indexOf(objects[j].get(0)) - 1), new IntInsnNode(Opcodes.BIPUSH, position));
                            }
                        }
                    }

                    final InsnList actualInstructions = new InsnList();
                    actualInstructions.add(new IntInsnNode(Opcodes.BIPUSH, position));
                    actualInstructions.add(node);
                    actualInstructions.add(new InsnNode(Opcodes.ASTORE));
                    actualInstructions.add(new InsnNode(Opcodes.DUP));
                    methodNode.instructions.insert(methodNode.instructions.get(methodNode.instructions.indexOf(objects[position - 1].get(objects[position - 1].size() - 1)) + 2), actualInstructions);

                    objects[position].add(node);
                });
            }
        }

        return Patch.Result.APPLY;
    }
}
