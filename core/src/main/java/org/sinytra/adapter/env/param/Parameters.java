package org.sinytra.adapter.env.param;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.selector.FrameUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Parameters {
    public static List<Parameter> parse(MethodNode method) {
        List<Type> types = getParameterTypes(method.desc);
        List<Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
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

    public static List<Type> getParameterTypes(String desc) {
        return new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
    }

    public static Map<VarInsnNode, Pair<Integer, Type>> gatherVarMappings(MethodNode method, List<Parameter> cleanParameters, List<Parameter> dirtyParameters, Map<Parameter, Parameter> replacements) {
        LocalVariableLookup lookup = new LocalVariableLookup(method);
        Map<Integer, Pair<Integer, Type>> map = replacements.entrySet().stream()
            .map(entry -> {
                int oldIndex = cleanParameters.indexOf(entry.getKey());
                if (oldIndex == -1) return null;

                int newIndex = dirtyParameters.indexOf(entry.getValue());
                if (newIndex == -1) return null;

                LocalVariableNode oldVar = lookup.getByParameterOrdinal(oldIndex);
                if (oldVar == null) return null;

                LocalVariableNode newVar = lookup.getByParameterOrdinal(newIndex);
                if (newVar == null) return null;

                return Pair.of(oldVar.index, Pair.of(newVar.index, entry.getValue().getType()));
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

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
        for (int i = 0; i < parameters.size(); i++) {
            Parameter param = parameters.get(i);

            // TODO Always re-apply all annotations or?
            // Remove old annotations
            Set<String> descs = param.getAnnotations().stream().map(Annotation::getDesc).collect(Collectors.toSet());
            final int finalI = i;
            Stream.of(method.visibleParameterAnnotations, method.invisibleParameterAnnotations)
                .filter(Objects::nonNull)
                .map(arr -> arr[finalI])
                .filter(Objects::nonNull)
                .forEach(list -> list.removeIf(n -> descs.contains(n.desc)));

            for (Annotation annotation : param.getAnnotations()) {
                AnnotationVisitor visitor = method.visitParameterAnnotation(i, annotation.getDesc(), annotation.isVisible());
                annotation.accept(visitor);
            }
        }
    }

    public static void retypeVariableAndReceivers(MethodNode methodNode, VarInsnNode targetVarInsn, int newIndex, String newOwner) {
        targetVarInsn.var = newIndex;

        Frame<SourceValue>[] frames = FrameUtil.getFrames(methodNode);
        for (AbstractInsnNode insn : methodNode.instructions) {
            // We only care about method invocations that have a receiver
            if (insn instanceof MethodInsnNode minsn && minsn.getOpcode() != Opcodes.INVOKESTATIC) {
                Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(insn)];
                if (frame == null || frame.getStackSize() <= 0) continue; // Dead code

                SourceValue receiver = frame.getStack(0);
                // Check if our target is one of the instructions that produced this value
                if (receiver.insns.contains(targetVarInsn)) {
                    minsn.owner = newOwner;
                }
            }
        }
    }

    private Parameters() {
    }
}
