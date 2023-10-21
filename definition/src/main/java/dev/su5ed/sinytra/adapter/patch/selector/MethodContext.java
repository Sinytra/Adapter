package dev.su5ed.sinytra.adapter.patch.selector;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, @Nullable AnnotationHandle injectionPointAnnotation, List<Type> targetTypes) {

    public MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes) {
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
    }

    public AnnotationHandle injectionPointAnnotationOrThrow() {
        return Objects.requireNonNull(injectionPointAnnotation, "Missing injection point annotation");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AnnotationValueHandle<?> classAnnotation;
        private AnnotationHandle methodAnnotation;
        private AnnotationHandle injectionPointAnnotation;
        private final List<Type> targetTypes = new ArrayList<>();

        public Builder classAnnotation(AnnotationValueHandle<?> annotation) {
            this.classAnnotation = annotation;
            return this;
        }

        public Builder methodAnnotation(AnnotationHandle annotation) {
            this.methodAnnotation = annotation;
            return this;
        }

        public Builder injectionPointAnnotation(AnnotationHandle annotation) {
            this.injectionPointAnnotation = annotation;
            return this;
        }
        
        public Builder targetTypes(List<Type> targetTypes) {
            this.targetTypes.addAll(targetTypes);
            return this;
        }

        public MethodContext build() {
            return new MethodContext(this.classAnnotation, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes));
        }
    }
}
