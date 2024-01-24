package dev.su5ed.sinytra.adapter.patch.transformer.param;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.LVTSnapshot;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ParameterTransformer {
    MethodQualifier WO_ORIGINAL_CALL = new MethodQualifier("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    Patch.Result apply(final ClassNode classNode, final MethodNode methodNode, final MethodContext methodContext, final PatchContext context, final List<Type> parameters, final int offset);

    default void withLVTSnapshot(final MethodNode methodNode, final Runnable action) {
        final LVTSnapshot snapshot = LVTSnapshot.take(methodNode);
        action.run();
        snapshot.applyDifference(methodNode);
    }

    @SuppressWarnings("DuplicatedCode") // The duplication is small
    default void extractWrapOperation(final MethodContext methodContext, final MethodNode methodNode, final List<Type> params, final Consumer<WrapOpModification> modification) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        if (!annotation.matchesDesc(MixinConstants.WRAP_OPERATION)) {
            return;
        }

        var wrapOpIndex = -1;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).getInternalName().equals(MixinConstants.OPERATION_INTERNAL_NAME)) {
                wrapOpIndex = i;
                break;
            }
        }

        if (wrapOpIndex < 0) {
            return;
        }

        boolean isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;

        int wrapOpLvt = isNonStatic ? 1 : 0;
        for (int i = 0; i < wrapOpIndex; i++) {
            wrapOpLvt += params.get(i).getSize();
        }

        for (int i = 0; i < methodNode.instructions.size(); i++) {
            var insn = methodNode.instructions.get(i);

            // A lot of assumptions ahead, beware. Here be dragons
            if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                // Backtrace until we find the load instruction of the operation var
                int loadInsnIndex = -1;
                for (int j = i - 1; j >= 0; j--) {
                    if (methodNode.instructions.get(j) instanceof VarInsnNode vin && vin.var == wrapOpLvt && vin.getOpcode() == Opcodes.ALOAD) {
                        loadInsnIndex = j;
                        break;
                    }
                }
                if (loadInsnIndex == -1) {
                    return;
                }

                // Assume that people are only calling `call` on the operation object
                if (!(
                        AdapterUtil.getIntConstValue(methodNode.instructions.get(loadInsnIndex + 1)).isPresent() &&
                        methodNode.instructions.get(loadInsnIndex + 2) instanceof TypeInsnNode n && n.getOpcode() == Opcodes.ANEWARRAY
                )) {
                    return;
                }

                final IntList removals = new IntArrayList();
                final Map<Integer, Consumer<InsnList>> insertions = new HashMap<>();
                final Map<Integer, Consumer<InsnList>> replacements = new HashMap<>();
                modification.accept(new WrapOpModification() {
                    @Override
                    public void insertParameter(int index, Consumer<InsnList> adapter) {
                        insertions.put(index, adapter);
                    }

                    @Override
                    public void replaceParameter(int index, Consumer<InsnList> adapter) {
                        replacements.put(index, adapter);
                    }

                    @Override
                    public void removeParameter(int index) {
                        removals.add(index);
                    }
                });

                final int oldArraySize = AdapterUtil.getIntConstValue(methodNode.instructions.get(loadInsnIndex + 1)).getAsInt();
                int newArrayLength = oldArraySize + insertions.size();

                methodNode.instructions.set(methodNode.instructions.get(loadInsnIndex + 1), AdapterUtil.getIntConstInsn(newArrayLength - removals.size()));

                List<AbstractInsnNode>[] objects = new List[newArrayLength];
                for (int j = 0; j < newArrayLength; j++) objects[j] = new ArrayList<>();

                // Find sequences of "PUSH/<CODE>/AASTORE/DUP" which represent the values of the vararg array
                int currentSequenceIndex = -1;
                // Skip the PUSH/ANEWARRAY/DUP for the array creation
                for (int j = loadInsnIndex + 4; j < i; j++) {
                    final var instruction = methodNode.instructions.get(j);
                    final var possibleIdx = AdapterUtil.getIntConstValue(instruction);
                    if (currentSequenceIndex == -1 && possibleIdx.isPresent()) {
                        currentSequenceIndex = possibleIdx.getAsInt();
                        continue;
                    }
                    if (currentSequenceIndex != -1) {
                        if (instruction.getOpcode() == Opcodes.AASTORE && (methodNode.instructions.get(j + 1).getOpcode() == Opcodes.DUP || currentSequenceIndex + 1 == oldArraySize)) {
                            j++;
                            currentSequenceIndex = -1;
                            continue;
                        }

                        objects[currentSequenceIndex].add(instruction);
                    }
                }

                int finalLoadInsnIndex = loadInsnIndex;
                insertions.forEach((position, target) -> {
                    if (position > 0) {
                        final var lastOfPrevious = methodNode.instructions.indexOf(objects[position - 1].get(objects[position - 1].size() - 1));
                        if (methodNode.instructions.get(lastOfPrevious + 2).getOpcode() != Opcodes.DUP) {
                            methodNode.instructions.insert(methodNode.instructions.get(lastOfPrevious + 1), new InsnNode(Opcodes.DUP));
                        }
                    }

                    if (!objects[position].isEmpty()) {
                        for (int j = newArrayLength - 1; j >= position; j--) {
                            objects[j] = objects[j - 1];
                            objects[j - 1] = new ArrayList<>();
                            if (!objects[j].isEmpty()) {
                                methodNode.instructions.set(methodNode.instructions.get(methodNode.instructions.indexOf(objects[j].get(0)) - 1), AdapterUtil.getIntConstInsn(j));
                            }
                        }
                    }

                    final InsnList actualInstructions = new InsnList();
                    actualInstructions.add(AdapterUtil.getIntConstInsn(position));
                    final InsnList sub = new InsnList();
                    target.accept(sub);
                    actualInstructions.add(sub);
                    actualInstructions.add(new InsnNode(Opcodes.AASTORE));
                    if (position < newArrayLength - 1) { // Last element doesn't need a DUP
                        actualInstructions.add(new InsnNode(Opcodes.DUP));
                    }

                    final int insertionTarget;
                    if (position == 0) {
                        // Inject after the DUP of the array
                        insertionTarget = finalLoadInsnIndex + 3;
                    } else {
                        insertionTarget = methodNode.instructions.indexOf(objects[position - 1].get(objects[position - 1].size() - 1)) + 2;
                    }

                    methodNode.instructions.insert(methodNode.instructions.get(insertionTarget), actualInstructions);

                    sub.forEach(objects[position]::add);
                });

                replacements.forEach((position, cons) -> {
                    if (objects[position].isEmpty()) {
                        return;
                    }

                    final InsnList actualInstructions = new InsnList();
                    actualInstructions.add(AdapterUtil.getIntConstInsn(position));
                    final InsnList sub = new InsnList();
                    cons.accept(sub);
                    actualInstructions.add(sub);
                    actualInstructions.add(new InsnNode(Opcodes.ASTORE));
                    actualInstructions.add(new InsnNode(Opcodes.DUP));
                    final var target = methodNode.instructions.get(methodNode.instructions.indexOf(objects[position].get(0)) - 1);
                    objects[position].forEach(methodNode.instructions::remove);
                    objects[position].clear();
                    methodNode.instructions.insert(target, actualInstructions);

                    sub.forEach(objects[position]::add);
                });


                removals.forEach((position) -> {
                    final var toRemove = objects[position];
                    for (int j = position + 1; j < newArrayLength; j++) {
                        objects[j - 1] = objects[j];
                        if (!objects[j - 1].isEmpty()) {
                            methodNode.instructions.set(methodNode.instructions.get(methodNode.instructions.indexOf(objects[j - 1].get(0)) - 1), AdapterUtil.getIntConstInsn(j - 1));
                        }
                    }
                    methodNode.instructions.remove(methodNode.instructions.get(methodNode.instructions.indexOf(toRemove.get(0)) - 1));
                    methodNode.instructions.remove(methodNode.instructions.get(methodNode.instructions.indexOf(toRemove.get(toRemove.size() - 1)) + 1));
                    methodNode.instructions.remove(methodNode.instructions.get(methodNode.instructions.indexOf(toRemove.get(toRemove.size() - 1)) + 2));
                    toRemove.forEach(methodNode.instructions::remove);
                });

                // Continue from the new position of the call
                i = methodNode.instructions.indexOf(minsn);
            }
        }

    }

    static int calculateLVTIndex(List<Type> parameters, boolean nonStatic, int index) {
        int lvt = nonStatic ? 1 : 0;
        for (int i = 0; i < index; i++) {
            lvt += parameters.get(i).getSize();
        }
        return lvt;
    }

    default Codec<? extends ParameterTransformer> codec() {
        throw new UnsupportedOperationException("This transform is not serializable");
    }

    interface WrapOpModification {
        void insertParameter(int index, Consumer<InsnList> adapter);
        void replaceParameter(int index, Consumer<InsnList> adapter);
        void removeParameter(int index);
    }
}
