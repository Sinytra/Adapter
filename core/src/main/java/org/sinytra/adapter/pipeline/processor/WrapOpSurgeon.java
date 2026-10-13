package org.sinytra.adapter.pipeline.processor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.WrapOpOriginalCall;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class WrapOpSurgeon {

    public static boolean tryUpgrade(MixinContext context, Recipe recipe, List<Type> methodParams, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        MethodNode methodNode = context.methodNode();
        LocalVariableLookup mixinLocals = LocalVariableLookup.of(methodNode);
        if (mixinLocals == null) return false;

        LocalVariableNode instanceLocal = mixinLocals.getByParameterOrdinalOrNull(0);
        if (instanceLocal == null) return false;
        int instanceIndex = instanceLocal.index;

        Multimap<Integer, VarInsnNode> usedVars = getUsedVars(mixinLocals, methodParams, context);
        Map<Integer, Pair<TypeAdapter, @Nullable Consumer<InsnList>>> adapters = new HashMap<>();

        for (Integer key : usedVars.keys()) {
            if (key == instanceIndex) {
                Pair<TypeAdapter, @Nullable Consumer<InsnList>> pair = findReplacementForInstance(cleanInsn, dirtyInsn, context, recipe, instanceIndex);
                if (pair != null) {
                    adapters.put(key, pair);
                    continue;
                }
            }
            return false;
        }

        usedVars.forEach((i, insn) -> {
            Pair<TypeAdapter, @Nullable Consumer<InsnList>> adapter = adapters.get(i);
            adapter.getFirst().apply(methodNode.instructions, insn);
        });

        for (Pair<TypeAdapter, @Nullable Consumer<InsnList>> pair : adapters.values()) {
            if (pair.getSecond() != null) {
                pair.getSecond().accept(methodNode.instructions);
            }
        }

        return true;
    }

    @Nullable
    private static Pair<TypeAdapter, @Nullable Consumer<InsnList>> findReplacementForInstance(MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn, MixinContext context, Recipe recipe, int instanceIndex) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) return null;

        List<List<AbstractInsnNode>> callArgInsns = context.dataFlow(cleanTarget).findOperandExpressions(cleanInsn);
        if (callArgInsns.isEmpty()) return null;

        List<AbstractInsnNode> receiverInsns = callArgInsns.getFirst();
        if (!receiverInsns.isEmpty() && receiverInsns.getFirst() instanceof VarInsnNode varInsn) {
            LocalVariableLookup cleanLookup = recipe.cleanLocalsTable();
            LocalVariableNode lvn = cleanLookup != null ? cleanLookup.getByIndexOrNull(varInsn.var) : null;
            if (lvn == null) {
                return null;
            }

            TypeAdapter typeAdapter = context.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getType(lvn.desc));
            if (typeAdapter == null) {
                return null;
            }

            List<AbstractInsnNode> subList = receiverInsns.subList(1, receiverInsns.size());

            TypeAdapter adapter = typeAdapter.andThen((list, insn) -> {
                // Fix receiver type originally changed by ReplaceParametersTransformer
                DataFlow.of(context.methodNode()).forEachReceiver((call, receiver) -> {
                    if (call.owner.equals(dirtyInsn.owner) && receiver.contains(insn)) {
                        call.owner = cleanInsn.owner;
                    }
                });

                list.insert(insn, AdapterUtil.insnList(AdapterUtil.cloneInsns(subList)));
            });
            Consumer<InsnList> castCheck = !subList.isEmpty() && subList.getFirst() instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.CHECKCAST ?
                list -> {
                    List<AbstractInsnNode> originalWOCall = new ArrayList<>(WrapOpOriginalCall.findFirstCallInsns(context.methodNode()));
                    if (originalWOCall.isEmpty()) {
                        return;
                    }
                    // Include final method call and cast
                    for (int i = 0; i < 3; i++) {
                        AbstractInsnNode next = originalWOCall.getLast().getNext();
                        if (next == null) {
                            return;
                        }
                        originalWOCall.add(next);
                    }
                    List<AbstractInsnNode> cloned = AdapterUtil.cloneInsns(originalWOCall);

                    boolean hasLabel = list.getFirst() instanceof LabelNode;
                    LabelNode label = hasLabel ? (LabelNode) list.getFirst() : new LabelNode();
                    InsnList check = AdapterUtil.insnList(
                        new LabelNode(),
                        new VarInsnNode(Opcodes.ALOAD, instanceIndex),
                        new TypeInsnNode(Opcodes.INSTANCEOF, typeInsn.desc),
                        new JumpInsnNode(Opcodes.IFNE, label),
                        new LabelNode()
                    );
                    typeAdapter.apply(check, check.get(1));
                    check.add(AdapterUtil.insnList(cloned));
                    check.add(new InsnNode(OpcodeUtil.getReturnOpcode(context.methodNode())));
                    if (!hasLabel) {
                        check.add(label);
                    }

                    list.insert(check);
                }
                : null;

            return Pair.of(adapter, castCheck);
        }
        return null;
    }

    public static Multimap<Integer, VarInsnNode> getUsedVars(LocalVariableLookup mixinLocals, List<Type> methodParams, MixinContext context) {
        MethodNode methodNode = context.methodNode();

        List<Integer> paramVars = IntStream.range(0, methodParams.size())
            .mapToObj(mixinLocals::getByParameterOrdinalOrNull)
            .filter(Objects::nonNull)
            .map(l -> l.index)
            .toList();

        List<AbstractInsnNode> originalOpCall = WrapOpOriginalCall.findFirstCallInsns(methodNode);
        Multimap<Integer, VarInsnNode> usedVars = HashMultimap.create();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && !originalOpCall.contains(insn) && paramVars.contains(varInsn.var)) {
                usedVars.put(varInsn.var, varInsn);
            }
        }

        return usedVars;
    }
}
