package org.sinytra.adapter.patch.processor.wrapop;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.types.TypeAdapter;
import org.sinytra.adapter.transform.param.ParamTransformationUtil;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class WrapOpSurgeon {

    public static boolean tryUpgrade(MixinContext context, Recipe recipe, List<Type> methodParams, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        MethodNode methodNode = context.methodNode();
        LocalVariableLookup mixinLocals = new LocalVariableLookup(methodNode);

        Multimap<Integer, VarInsnNode> usedVars = getUsedVars(mixinLocals, methodParams, context);
        Map<Integer, Pair<TypeAdapter, @Nullable Consumer<InsnList>>> adapters = new HashMap<>();

        for (Integer key : usedVars.keys()) {
            if (key == 0) {
                Pair<TypeAdapter, @Nullable Consumer<InsnList>> pair = findReplacementForInstance(cleanInsn, dirtyInsn, context, recipe);
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
    private static Pair<TypeAdapter, @Nullable Consumer<InsnList>> findReplacementForInstance(MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn, MixinContext context, Recipe recipe) {
        MethodNode cleanTargetMethod = recipe.getCleanTarget().methodNode();
        List<AbstractInsnNode> receiverInsns = MethodCallAnalyzer.getMethodCallArgInsns(cleanTargetMethod, cleanInsn).getFirst();
        if (!receiverInsns.isEmpty() && receiverInsns.getFirst() instanceof VarInsnNode varInsn) {
            BytecodeFixerUpper bfu = context.patchContext().environment().bytecodeFixerUpper();
            if (bfu == null) {
                return null;
            }

            LocalVariableLookup cleanLookup = recipe.cleanLocalsTable();
            LocalVariableNode lvn = cleanLookup.getByIndex(varInsn.var);

            TypeAdapter typeAdapter = bfu.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getType(lvn.desc));
            if (typeAdapter == null) {
                return null;
            }

            List<AbstractInsnNode> subList = receiverInsns.subList(1, receiverInsns.size());

            TypeAdapter adapter = typeAdapter.andThen((list, insn) -> {
                // Fix receiver type originally changed by ReplaceParametersTransformer
                for (AbstractInsnNode next = insn.getNext(); next != null && !(next instanceof LabelNode); next = next.getNext()) {
                    if (next instanceof MethodInsnNode minsn && minsn.owner.equals(dirtyInsn.owner)) {
                        List<AbstractInsnNode> args = MethodCallAnalyzer.getMethodCallInsns(context.methodNode(), minsn);
                        if (args.contains(insn)) {
                            minsn.owner = cleanInsn.owner;
                        }
                    }
                }

                list.insert(insn, AdapterUtil.insnList(AdapterUtil.cloneInsns(subList)));
            });
            Consumer<InsnList> castCheck = subList.getFirst() instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.CHECKCAST ?
                list -> {
                    List<AbstractInsnNode> originalWOCall = new ArrayList<>(ParamTransformationUtil.findWrapOperationOriginalCallArgs(context.methodNode()));
                    // Include final method call and cast
                    originalWOCall.add(originalWOCall.getLast().getNext());
                    originalWOCall.add(originalWOCall.getLast().getNext());
                    originalWOCall.add(originalWOCall.getLast().getNext());
                    List<AbstractInsnNode> cloned = AdapterUtil.cloneInsns(originalWOCall);

                    boolean hasLabel = list.getFirst() instanceof LabelNode;
                    LabelNode label = hasLabel ? (LabelNode) list.getFirst() : new LabelNode();
                    InsnList check = AdapterUtil.insnList(
                        new LabelNode(),
                        new VarInsnNode(Opcodes.ALOAD, 0),
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
            .mapToObj(mixinLocals::getByParameterOrdinal)
            .map(l -> l.index)
            .toList();

        List<AbstractInsnNode> originalOpCall = ParamTransformationUtil.findWrapOperationOriginalCallArgs(methodNode);
        Multimap<Integer, VarInsnNode> usedVars = HashMultimap.create();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && !originalOpCall.contains(insn) && paramVars.contains(varInsn.var)) {
                usedVars.put(varInsn.var, varInsn);
            }
        }

        return usedVars;
    }
}
