package org.sinytra.adapter.model.param;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

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

    private Parameters() {
    }
}
