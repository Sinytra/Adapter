package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.InjectionPointMatcher;
import dev.su5ed.sinytra.adapter.patch.selector.MethodMatcher;
import dev.su5ed.sinytra.adapter.patch.serialization.MethodTransformSerialization;
import dev.su5ed.sinytra.adapter.patch.transformer.*;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ClassPatchInstance extends PatchInstance {
    public static final Codec<ClassPatchInstance> CODEC = RecordCodecBuilder
        .<ClassPatchInstance>create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("targetClasses", List.of()).forGetter(p -> p.targetClasses),
            MethodMatcher.CODEC.listOf().optionalFieldOf("targetMethods", List.of()).forGetter(p -> p.targetMethods),
            InjectionPointMatcher.CODEC.listOf().optionalFieldOf("targetInjectionPoints", List.of()).forGetter(p -> p.targetInjectionPoints),
            Codec.STRING.listOf().optionalFieldOf("targetAnnotations", List.of()).forGetter(p -> p.targetAnnotations),
            MethodTransformSerialization.METHOD_TRANSFORM_CODEC.listOf().fieldOf("transforms").forGetter(p -> p.transforms)
        ).apply(instance, ClassPatchInstance::new))
        .flatComapMap(Function.identity(), obj -> obj.targetAnnotationValues != null ? DataResult.error(() -> "Cannot serialize targetAnnotationValues") : DataResult.success(obj));

    private final List<MethodMatcher> targetMethods;
    private final List<InjectionPointMatcher> targetInjectionPoints;

    private ClassPatchInstance(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, List<MethodTransform> transforms) {
        this(targetClasses, targetMethods, targetInjectionPoints, targetAnnotations, map -> true, List.of(), transforms);
    }

    private ClassPatchInstance(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, Predicate<AnnotationHandle> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        super(targetClasses, targetAnnotations, targetAnnotationValues, classTransforms, transforms);

        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
    }

    @Override
    public Codec<? extends PatchInstance> codec() {
        return CODEC;
    }

    @Override
    protected boolean checkAnnotation(String owner, MethodNode method, AnnotationHandle methodAnnotation, PatchEnvironment remaper, MethodContextImpl.Builder builder) {
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
            return transform(new ModifyInjectionPoint(value, target, resetValues));
        }

        @Override
        public ClassPatchBuilder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer) {
            return transform(new RedirectShadowMethod(original, target, callFixer));
        }

        @Override
        public ClassPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher) {
            return transform(new DivertRedirectorTransform(patcher));
        }

        @Override
        public ClassPatchBuilder updateRedirectTarget(String originalTarget, String newTarget) {
            return targetInjectionPoint(originalTarget)
                .transform(new ModifyRedirectToWrapper(
                    MethodQualifier.create(originalTarget).orElseThrow(),
                    MethodQualifier.create(newTarget).orElseThrow()
                ))
                .modifyMixinType(MixinConstants.WRAP_OPERATION, b -> b
                    .sameTarget()
                    .injectionPoint("INVOKE", newTarget));
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
