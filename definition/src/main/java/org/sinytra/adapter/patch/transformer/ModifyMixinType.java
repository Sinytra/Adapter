package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record ModifyMixinType(String replacementDesc, Consumer<Builder> consumer) implements MethodTransform {

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        AnnotationNode replacement = new AnnotationNode(this.replacementDesc);
        Builder builder = new Builder(annotation.getAllValues(), replacement);
        this.consumer.accept(builder);
        for (int i = 0; i < methodNode.visibleAnnotations.size(); i++) {
            AnnotationNode methodAnn = methodNode.visibleAnnotations.get(i);
            if (methodAnn == annotation.unwrap()) {
                methodNode.visibleAnnotations.set(i, replacement);
                annotation.refresh(replacement);
                break;
            }
        }
        return Patch.Result.APPLY;
    }

    public static class Builder {
        private final Map<String, AnnotationValueHandle<?>> annotationValues;
        private final AnnotationNode replacement;

        public Builder(Map<String, AnnotationValueHandle<?>> annotationValues, AnnotationNode replacement) {
            this.annotationValues = annotationValues;
            this.replacement = replacement;
        }

        public Builder sameTarget() {
            AnnotationValueHandle<List<String>> method = (AnnotationValueHandle<List<String>>) this.annotationValues.get("method");
            if (method != null) {
                putValue("method", method.get());
            }
            return this;
        }

        public Builder putValue(String key, Object value) {
            this.replacement.visit(key, value);
            return this;
        }

        public Builder injectionPoint(String value) {
            return injectionPoint(value, "");
        }

        public Builder injectionPoint(String value, String target) {
            return injectionPoint(value, target, b -> {});
        }

        public Builder injectionPoint(String value, String target, Consumer<AnnotationVisitor> builder) {
            AnnotationVisitor atNode = this.replacement.visitAnnotation("at", MixinConstants.AT);
            atNode.visit("value", value);
            if (!target.isEmpty()) {
                atNode.visit("target", target);
            }
            builder.accept(atNode);
            return this;
        }
    }
}
