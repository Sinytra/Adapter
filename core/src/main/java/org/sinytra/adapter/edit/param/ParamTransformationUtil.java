package org.sinytra.adapter.edit.param;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.model.param.Annotation;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ParamTransformationUtil {
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

    public static Map<VarInsnNode, Pair<Integer, Type>> gatherVarMappings(MethodNode method, List<Parameter> cleanParameters, List<Parameter> dirtyParameters, Map<Parameter, Parameter> replacements) {
        LocalVariableLookup lookup = method.localVariables == null ? null : new LocalVariableLookup(method);
        Map<Integer, Pair<Integer, Type>> map = replacements.entrySet().stream()
            .map(entry -> {
                int oldIndex = cleanParameters.indexOf(entry.getKey());
                if (oldIndex == -1) return null;

                int newIndex = dirtyParameters.indexOf(entry.getValue());
                if (newIndex == -1) return null;

                LocalVariableNode oldVar = lookup == null ? null : lookup.getByParameterOrdinalOrNull(oldIndex);
                if (oldVar == null) return null;

                LocalVariableNode newVar = lookup.getByParameterOrdinalOrNull(newIndex);
                if (newVar == null) return null;

                return Pair.of(oldVar.index, Pair.of(newVar.index, entry.getValue().type()));
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (first, second) -> first));

        Map<VarInsnNode, Pair<Integer, Type>> varMap = new HashMap<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof VarInsnNode varInsn && map.containsKey(varInsn.var)) {
                varMap.put(varInsn, map.get(varInsn.var));
            }
        }
        return varMap;
    }

    public static void applyVarMappings(MethodNode methodNode, Map<VarInsnNode, Pair<Integer, Type>> map) {
        map.forEach((i, v) -> retypeVariableAndReceivers(methodNode, i, v.getFirst(), v.getSecond().getInternalName()));
    }

    public static void applyAnnotations(MethodNode method, List<Parameter> parameters) {
        updateParameterAnnotationArrays(method, Type.getArgumentCount(method.desc), annotations -> {});

        for (int i = 0; i < parameters.size(); i++) {
            Parameter param = parameters.get(i);

            // TODO Always re-apply all annotations or?
            // Remove old annotations
            Set<String> descs = param.annotations().stream().map(Annotation::getDesc).collect(Collectors.toSet());
            final int finalI = i;
            Stream.of(method.visibleParameterAnnotations, method.invisibleParameterAnnotations)
                .filter(arr -> arr != null && finalI < arr.length)
                .map(arr -> arr[finalI])
                .filter(Objects::nonNull)
                .forEach(list -> list.removeIf(n -> descs.contains(n.desc)));

            for (Annotation annotation : param.annotations()) {
                AnnotationVisitor visitor = method.visitParameterAnnotation(i, annotation.getDesc(), annotation.isVisible());
                annotation.accept(visitor);
            }
        }
    }

    public static void retypeVariableAndReceivers(MethodNode methodNode, VarInsnNode targetVarInsn, int newIndex, String newOwner) {
        targetVarInsn.var = newIndex;
        DataFlow.of(methodNode).forEachReceiver((call, receiver) -> {
            if (receiver.contains(targetVarInsn)) {
                call.owner = newOwner;
            }
        });
    }
}
