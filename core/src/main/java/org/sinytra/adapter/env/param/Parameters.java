package org.sinytra.adapter.env.param;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.transform.param.ParamTransformationUtil;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Parameters {
    public static List<Parameter> parse(MethodNode method) {
        return parse(method, -1, -1);
    }

    public static List<Parameter> parse(MethodNode method, int start, int end) {
        List<Type> types = getParameterTypes(method.desc);
        List<Parameter> parameters = new ArrayList<>();
        
        int first = start == -1 ? 0 : start;
        int last = end == -1 ? types.size() : end;
        for (int i = first; i < last; i++) {
            Type type = types.get(i);
            Parameter.Builder builder = Parameter.builder(type);
            parseAnnotations(builder, method.invisibleParameterAnnotations, false, i);
            parseAnnotations(builder, method.visibleParameterAnnotations, true, i);
            Parameter parameter = builder.build();

            parameters.add(parameter);
        }

        return parameters;
    }

    private static void parseAnnotations(Parameter.Builder builder, @Nullable List<AnnotationNode>[] source, boolean visible, int index) {
        if (source != null && source.length > index) {
            List<AnnotationNode> annotations = source[index];
            if (annotations != null) {
                for (AnnotationNode node : annotations) {
                    Annotation parsed = Annotation.parse(node, visible);
                    builder.annotate(parsed);
                }
            }
        }
    }

    /**
     * {@return the stack operands of a call: its receiver (if any), followed by its parameters}
     */
    public static List<Type> getCallOperandTypes(@Nullable Type receiver, String desc) {
        List<Type> types = getParameterTypes(desc);
        if (receiver != null) {
            types.addFirst(receiver);
        }
        return types;
    }

    public static List<Type> getParameterTypes(String desc) {
        return new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
    }

    public static Map<VarInsnNode, Pair<Integer, Type>> gatherVarMappings(MethodNode method, List<Parameter> cleanParameters, List<Parameter> dirtyParameters, Map<Parameter, Parameter> replacements, MixinContext context) {
        LocalVariableLookup lookup = context.methods().getLVT(method);
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
        ParamTransformationUtil.updateParameterAnnotationArrays(method, Type.getArgumentCount(method.desc), annotations -> {});

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
        MethodAnalyzer.forEachReceiver(methodNode, (call, receiver) -> {
            if (receiver.contains(targetVarInsn)) {
                call.owner = newOwner;
            }
        });
    }

    private Parameters() {
    }
}
