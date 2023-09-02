package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.util.MixinConstants;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record ModifyMixinType(String replacementDesc, Consumer<Builder> consumer) implements MethodTransform {

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        AnnotationNode replacement = new AnnotationNode(this.replacementDesc);
        Builder builder = new Builder(annotationValues, replacement);
        this.consumer.accept(builder);
        for (int i = 0; i < methodNode.visibleAnnotations.size(); i++) {
            AnnotationNode methodAnn = methodNode.visibleAnnotations.get(i);
            if (methodAnn == annotation) {
                methodNode.visibleAnnotations.set(i, replacement);
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
            AnnotationVisitor atNode = this.replacement.visitAnnotation("at", MixinConstants.AT);
            atNode.visit("value", value);
            if (!target.isEmpty()) {
                atNode.visit("target", target);
            }
            return this;
        }
    }
}
