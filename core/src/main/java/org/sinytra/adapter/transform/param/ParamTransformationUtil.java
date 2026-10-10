package org.sinytra.adapter.transform.param;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ParamTransformationUtil {
    private static final MethodQualifier WO_ORIGINAL_CALL = new MethodQualifier("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    public static void updateParameterAnnotationArrays(MethodNode methodNode, int paramCount, Consumer<List<List<AnnotationNode>>> action) {
        methodNode.visibleParameterAnnotations = updateParameterAnnotations(methodNode.visibleParameterAnnotations, paramCount, action);
        methodNode.invisibleParameterAnnotations = updateParameterAnnotations(methodNode.invisibleParameterAnnotations, paramCount, action);
        // Derive the count from the final descriptor later
        methodNode.visibleAnnotableParameterCount = 0;
        methodNode.invisibleAnnotableParameterCount = 0;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] updateParameterAnnotations(@Nullable List<AnnotationNode>[] annotations, int paramCount, Consumer<List<List<AnnotationNode>>> action) {
        if (annotations == null) {
            return null;
        }
        List<List<AnnotationNode>> list = new ArrayList<>(Arrays.asList(annotations));
        while (list.size() < paramCount) {
            list.add(null);
        }
        action.accept(list);
        return list.toArray(List[]::new);
    }

    public static void reorderParameters(MethodNode methodNode, List<Type> parameters, boolean nonStatic, int[] order) {
        List<Type> permuted = Arrays.stream(order).mapToObj(parameters::get).toList();

        Int2IntMap slots = new Int2IntOpenHashMap();
        for (int pos = 0; pos < order.length; pos++) {
            slots.put(getLVTIndex(parameters, nonStatic, order[pos]), getLVTIndex(permuted, nonStatic, pos));
        }
        AdapterUtil.replaceLVT(methodNode, idx -> slots.getOrDefault(idx, idx));
        if (methodNode.localVariables != null) {
            for (LocalVariableNode lvn : methodNode.localVariables) {
                lvn.index = slots.getOrDefault(lvn.index, lvn.index);
            }
        }

        updateParameterAnnotationArrays(methodNode, parameters.size(), annotations -> {
            List<List<AnnotationNode>> original = new ArrayList<>(annotations);
            for (int pos = 0; pos < order.length; pos++) {
                annotations.set(pos, original.get(order[pos]));
            }
        });

        for (int pos = 0; pos < order.length; pos++) {
            parameters.set(pos, permuted.get(pos));
        }
    }

    public static int getLVTIndex(List<Type> parameters, boolean nonStatic, int index) {
        int lvt = nonStatic ? 1 : 0;
        for (int i = 0; i < index; i++) {
            lvt += parameters.get(i).getSize();
        }
        return lvt;
    }

    public static List<AbstractInsnNode> findWrapOperationOriginalCall(MethodNode methodNode) {
        List<AbstractInsnNode> list = new ArrayList<>();
        outer:
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                    if (prev instanceof LabelNode) {
                        continue outer;
                    }
                    if (AdapterUtil.canHandleLocalVarInsnValue(prev)) {
                        list.add(prev);
                    }
                }
            }
        }
        return List.copyOf(list);
    }

    public static List<AbstractInsnNode> findWrapOperationOriginalCallArgs(MethodNode methodNode) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                return Objects.requireNonNullElseGet(MethodCallAnalyzer.getMethodCallInsns(methodNode, minsn), List::of);
            }
        }
        return List.of();
    }
}
