package dev.su5ed.sinytra.adapter.patch.selector;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, @Nullable AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets) {
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
        this.matchingTargets = Objects.requireNonNull(matchingTargets, "Missing matching targets");
    }

    public AnnotationHandle injectionPointAnnotationOrThrow() {
        return Objects.requireNonNull(injectionPointAnnotation, "Missing injection point annotation");
    }

    @Nullable
    public Pair<ClassNode, MethodNode> findInjectionTarget(AnnotationHandle annotation, PatchContext context, Function<String, ClassNode> classLookup) {
        // Find target method qualifier
        MethodQualifier qualifier = getTargetMethodQualifier(annotation, context);
        if (qualifier == null || !qualifier.isFull()) {
            return null;
        }
        // Find target class
        ClassNode targetClass = classLookup.apply(qualifier.internalOwnerName());
        if (targetClass == null) {
            return null;
        }
        // Find target method in class
        MethodNode targetMethod = targetClass.methods.stream().filter(mtd -> mtd.name.equals(qualifier.name()) && mtd.desc.equals(qualifier.desc())).findFirst().orElse(null);
        if (targetMethod == null) {
            LOGGER.debug("Target method not found: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        }
        return Pair.of(targetClass, targetMethod);
    }

    @Nullable
    public MethodQualifier getTargetMethodQualifier(AnnotationHandle annotation, PatchContext context) {
        // Get method targets
        List<String> methodRefs = annotation.<List<String>>getValue("method").orElseThrow().get();
        if (methodRefs.size() > 1) {
            // We only support single method targets for now
            return null;
        }
        // Resolve method reference
        String reference = context.remap(methodRefs.get(0));
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference, false).orElse(null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AnnotationValueHandle<?> classAnnotation;
        private AnnotationHandle methodAnnotation;
        private AnnotationHandle injectionPointAnnotation;
        private final List<Type> targetTypes = new ArrayList<>();
        private final List<String> matchingTargets = new ArrayList<>();

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

        public Builder matchingTargets(List<String> matchingTargets) {
            this.matchingTargets.addAll(matchingTargets);
            return this;
        }

        public MethodContext build() {
            return new MethodContext(this.classAnnotation, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes), List.copyOf(this.matchingTargets));
        }
    }
}
