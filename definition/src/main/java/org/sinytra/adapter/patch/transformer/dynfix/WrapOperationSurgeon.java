package org.sinytra.adapter.patch.transformer.dynfix;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.MethodUpgrader;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformationUtil;
import org.sinytra.adapter.patch.transformer.operation.CompoundMethodTransform;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.*;
import java.util.function.Consumer;

public class WrapOperationSurgeon {

    public static Patch.Result tryUpgrade(MethodContext methodContext, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        MethodNode methodNode = methodContext.getMixinMethod();
        LocalVariableLookup mixinLocals = new LocalVariableLookup(methodNode);

        Multimap<Integer, VarInsnNode> usedVars = getUsedVars(mixinLocals, methodContext);
        Map<Integer, Pair<TypeAdapter, @Nullable Consumer<InsnList>>> adapters = new HashMap<>();

        for (Integer key : usedVars.keys()) {
            if (key == 0) {
                Pair<TypeAdapter, @Nullable Consumer<InsnList>> pair = findReplacementForInstance(cleanInsn, dirtyInsn, methodContext);
                if (pair != null) {
                    adapters.put(key, pair);
                    continue;
                }
            }
            return Patch.Result.PASS;
        }

        MethodQualifier oldQualifier = methodContext.getInjectionPointMethodQualifier();
        String newQualifier = MethodCallAnalyzer.getCallQualifier(dirtyInsn);
        return CompoundMethodTransform.builder(b -> b.modifyInjectionPoint("INVOKE", newQualifier, false, true))
            .onSuccess(() -> (c, m, mtx, ctx) -> {
                MethodUpgrader.upgradeWrapOperationLayered(methodContext, oldQualifier, MethodQualifier.create(newQualifier).orElseThrow());

                usedVars.forEach((i, insn) -> {
                    Pair<TypeAdapter, @Nullable Consumer<InsnList>> adapter = adapters.get(i);
                    adapter.getFirst().apply(methodNode.instructions, insn);
                });

                for (Pair<TypeAdapter, @Nullable Consumer<InsnList>> pair : adapters.values()) {
                    if (pair.getSecond() != null) {
                        pair.getSecond().accept(methodNode.instructions);
                    }
                }

                return Patch.Result.APPLY;
            })
            .apply(methodContext);
    }

    @Nullable
    private static Pair<TypeAdapter, @Nullable Consumer<InsnList>> findReplacementForInstance(MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn, MethodContext methodContext) {
        MethodNode cleanTargetMethod = methodContext.findCleanInjectionTarget().methodNode();
        List<AbstractInsnNode> receiverInsns = getMethodInvocationsInsns(cleanTargetMethod, cleanInsn, 0);
        if (!receiverInsns.isEmpty() && receiverInsns.getFirst() instanceof VarInsnNode varInsn) {
            BytecodeFixerUpper bfu = methodContext.patchContext().environment().bytecodeFixerUpper();
            if (bfu == null) {
                return null;
            }

            LocalVariableLookup cleanLookup = methodContext.cleanLocalsTable();
            LocalVariableNode lvn = cleanLookup.getByIndex(varInsn.var);

            TypeAdapter typeAdapter = bfu.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getType(lvn.desc));
            if (typeAdapter == null) {
                return null;
            }

            List<AbstractInsnNode> subList = receiverInsns.subList(1, receiverInsns.size());

            TypeAdapter adapter = typeAdapter.andThen((list, insn) -> {
                list.insert(insn, AdapterUtil.insnList(AdapterUtil.cloneInsns(subList)));
            });
            Consumer<InsnList> castCheck = subList.getFirst() instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.CHECKCAST ?
                list -> {
                    List<AbstractInsnNode> originalWOCall = new ArrayList<>(ParamTransformationUtil.findWrapOperationOriginalCallArgs(methodContext.getMixinMethod(), methodContext));
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
                    check.add(new InsnNode(OpcodeUtil.getReturnOpcode(methodContext.getMixinMethod())));
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

    @Nullable
    private static List<AbstractInsnNode> getMethodInvocationsInsns(MethodNode method, MethodInsnNode minsn, int param) {
        List<AbstractInsnNode> insns = new ArrayList<>();
        if (getMethodInvocationsInsns(method, minsn, param, insns)) {
            return insns;
        }
        return null;
    }

    @Nullable
    private static boolean getMethodInvocationsInsns(MethodNode method, MethodInsnNode minsn, int param, List<AbstractInsnNode> insns) {
        List<AbstractInsnNode> invocationInsns = MethodCallAnalyzer.findMethodCallParamInsns(method, minsn);
        if (invocationInsns.isEmpty()) {
            return false;
        }
        AbstractInsnNode receiver = invocationInsns.getFirst();
        if (receiver instanceof VarInsnNode) {
            insns.addAll(0, invocationInsns);
            return true;
        } else if (receiver instanceof TypeInsnNode typeInsn && receiver.getPrevious() instanceof VarInsnNode varInsn) {
            insns.addFirst(typeInsn);
            insns.addFirst(varInsn);
            return true;
        } else if (receiver instanceof MethodInsnNode invocation) {
            List<AbstractInsnNode> methodInsns = new ArrayList<>();
            if (getMethodInvocationsInsns(method, invocation, param, methodInsns)) {
                insns.add(invocationInsns.get(param));
                insns.addAll(0, methodInsns);
                return true;
            }
        }
        return false;
    }

    private static Multimap<Integer, VarInsnNode> getUsedVars(LocalVariableLookup mixinLocals, MethodContext methodContext) {
        MethodNode methodNode = methodContext.getMixinMethod();
        Type[] argsTypes = Type.getArgumentTypes(methodNode.desc);
        Set<Integer> paramVars = new HashSet<>();
        for (int i = 0; i < argsTypes.length; i++) {
            if (argsTypes[i].equals(AdapterUtil.OPERATION_TYPE)) {
                break;
            }
            LocalVariableNode lvn = mixinLocals.getByParameterOrdinal(i);
            paramVars.add(lvn.index);
        }

        List<AbstractInsnNode> originalOpCall = ParamTransformationUtil.findWrapOperationOriginalCallArgs(methodNode, methodContext);
        Multimap<Integer, VarInsnNode> usedVars = HashMultimap.create();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && !originalOpCall.contains(insn) && paramVars.contains(varInsn.var)) {
                usedVars.put(varInsn.var, varInsn);
            }
        }

        return usedVars;
    }
}
