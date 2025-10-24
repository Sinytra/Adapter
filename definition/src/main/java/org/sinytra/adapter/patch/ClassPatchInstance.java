package org.sinytra.adapter.patch;

import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.analysis.selector.InjectionPointMatcher;
import org.sinytra.adapter.patch.analysis.selector.MethodMatcher;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.transformer.operation.unit.DisableMixin;
import org.sinytra.adapter.patch.transformer.operation.unit.DivertRedirectorTransform;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionPoint;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClassPatchInstance extends PatchInstance {
    private final List<MethodMatcher> targetMethods;
    private final List<InjectionPointMatcher> targetInjectionPoints;

    private ClassPatchInstance(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, Predicate<AnnotationHandle> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        super(targetClasses, targetAnnotations, targetAnnotationValues, classTransforms, transforms);

        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
    }

    @Override
    protected boolean checkAnnotation(String owner, MethodNode method, AnnotationHandle methodAnnotation, PatchEnvironment remaper, MethodContextImpl.Builder builder) {
        builder.methodNode(method);
        builder.methodAnnotation(methodAnnotation);
        if (methodAnnotation.matchesDesc(MixinConstants.OVERWRITE)) {
            return this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(method.name, method.desc));
        } else if (KNOWN_MIXIN_TYPES.contains(methodAnnotation.getDesc())) {
            return methodAnnotation.<List<String>>getValue("method")
                .map(value -> {
                    List<String> matchingTargets = new ArrayList<>();
                    for (String target : value.get()) {
                        String remappedTarget = remaper.refmapHolder().remap(owner, target);
                        MethodQualifier qualifier = MethodQualifier.create(remappedTarget).filter(q -> q.name() != null).orElse(null);
                        if (qualifier == null) {
                            continue;
                        }
                        String targetName = qualifier.name();
                        String targetDesc = qualifier.desc();
                        if ((this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(targetName, targetDesc)))
                            // Must call checkInjectionPoint first so that any present @At annotation is added to the method context builder
                            && checkInjectionPoint(owner, methodAnnotation, remaper, builder)
                        ) {
                            matchingTargets.add(target);
                        }
                    }
                    builder.matchingTargets(matchingTargets);
                    return !matchingTargets.isEmpty();
                })
                .orElse(false);
        }
        return false;
    }

    private boolean checkInjectionPoint(String owner, AnnotationHandle methodAnnotation, PatchEnvironment environment, MethodContextImpl.Builder builder) {
        return methodAnnotation.getNested("at")
            .flatMap(node -> checkInjectionPointAnnotation(owner, node, environment, builder))
            // Check slice.from target
            .or(() -> methodAnnotation.<AnnotationNode>getValue("slice")
                .flatMap(slice -> slice.findNested("from")
                    .flatMap(from -> checkInjectionPointAnnotation(owner, from, environment, builder))))
            .orElse(this.targetInjectionPoints.isEmpty());
    }

    private Optional<Boolean> checkInjectionPointAnnotation(String owner, AnnotationHandle injectionPointAnnotation, PatchEnvironment environment, MethodContextImpl.Builder builder) {
        AnnotationValueHandle<String> value = injectionPointAnnotation.<String>getValue("value").orElse(null);
        String valueStr = value != null ? value.get() : null;
        String targetStr = injectionPointAnnotation.<String>getValue("target").map(t -> environment.refmapHolder().remap(owner, t.get())).orElse("");
        if (this.targetInjectionPoints.isEmpty() || this.targetInjectionPoints.stream().anyMatch(pred -> pred.test(valueStr, targetStr))) {
            builder.injectionPointAnnotation(injectionPointAnnotation);
            return Optional.of(true);
        }
        return Optional.empty();
    }

    public static class ClassPatchBuilderImpl extends BaseBuilder<ClassPatchBuilder> implements ClassPatchBuilder {
        private final Set<MethodMatcher> targetMethods = new HashSet<>();
        private final Set<InjectionPointMatcher> targetInjectionPoints = new HashSet<>();

        @Override
        public ClassPatchBuilder targetMethod(String... targets) {
            for (String target : targets) {
                this.targetMethods.add(new MethodMatcher(target));
            }
            return this;
        }

        @Override
        public ClassPatchBuilder targetInjectionPoint(String value, String target) {
            this.targetInjectionPoints.add(new InjectionPointMatcher(value, target));
            return this;
        }

        @Override
        public ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues) {
            return modifyInjectionPoint(value, target, resetValues, false);
        }

        @Override
        public ClassPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues, boolean dontUpgrade) {
            return transform(new ModifyInjectionPoint(value, target, resetValues, dontUpgrade));
        }

        @Override
        public ClassPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher) {
            return transform(new DivertRedirectorTransform(patcher));
        }

        @Override
        public ClassPatchBuilder disable() {
            return transform(DisableMixin.INSTANCE);
        }

        @Override
        public PatchInstance build() {
            return new ClassPatchInstance(
                List.copyOf(this.targetClasses),
                List.copyOf(this.targetMethods),
                List.copyOf(this.targetInjectionPoints),
                List.copyOf(this.targetAnnotations),
                this.targetAnnotationValues,
                List.copyOf(this.classTransforms),
                List.copyOf(this.transforms)
            );
        }
    }
}
