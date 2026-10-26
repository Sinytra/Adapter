package org.sinytra.adapter.model.ann;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.model.constant.MixinAnnotations;

import java.util.List;

public record ExpressionData(String expression, List<TypeDefinition> definitions) {
    public record TypeDefinition(String id, Type type) {
    }

    public List<AnnotationNode> toAnnotationNodes() {
        AnnotationNode expression = new AnnotationNode(MixinAnnotations.EXPRESSION);
        expression.visit("value", List.of(this.expression));
        List<AnnotationNode> definitions = this.definitions.stream()
            .map(definition -> {
                AnnotationNode node = new AnnotationNode(MixinAnnotations.DEFINITION);
                node.visit("id", definition.id());
                node.visit("type", List.of(definition.type()));
                return node;
            })
            .toList();
        if (definitions.size() == 1) {
            return List.of(definitions.getFirst(), expression);
        }
        AnnotationNode container = new AnnotationNode(MixinAnnotations.DEFINITIONS);
        container.visit("value", definitions);
        return List.of(container, expression);
    }

    public static boolean isExpressionAnnotation(AnnotationNode annotation) {
        return switch (annotation.desc) {
            case MixinAnnotations.EXPRESSION, MixinAnnotations.EXPRESSIONS, MixinAnnotations.DEFINITION, MixinAnnotations.DEFINITIONS -> true;
            default -> false;
        };
    }
}
