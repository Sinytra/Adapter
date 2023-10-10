package dev.su5ed.sinytra.adapter.patch.selector;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, @Nullable AnnotationHandle injectionPointAnnotation) {

    public MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation) {
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
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

        public MethodContext build() {
            return new MethodContext(this.classAnnotation, this.methodAnnotation, this.injectionPointAnnotation);
        }
    }
}
