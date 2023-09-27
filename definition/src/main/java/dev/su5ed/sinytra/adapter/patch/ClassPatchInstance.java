package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.selector.InjectionPointMatcher;
import dev.su5ed.sinytra.adapter.patch.selector.MethodMatcher;
import dev.su5ed.sinytra.adapter.patch.serialization.MethodTransformSerialization;
import dev.su5ed.sinytra.adapter.patch.transformer.DisableMixin;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyInjectionPoint;
import dev.su5ed.sinytra.adapter.patch.transformer.RedirectShadowMethod;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ClassPatchInstance extends PatchInstance {
    public static final String OWNER_PREFIX = "^(?<owner>L(?:.*?)+;)";

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

    private ClassPatchInstance(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        super(targetClasses, targetAnnotations, targetAnnotationValues, classTransforms, transforms);

        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
    }

    @Override
    public Codec<? extends PatchInstance> codec() {
        return CODEC;
    }

    protected Optional<Map<String, AnnotationValueHandle<?>>> checkAnnotation(String owner, MethodNode method, AnnotationNode annotation, PatchEnvironment remaper) {
        if (annotation.desc.equals(Patch.OVERWRITE)) {
            if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(method.name, method.desc))) {
                return Optional.of(Map.of());
            }
        } else if (KNOWN_MIXIN_TYPES.contains(annotation.desc)) {
            return PatchInstance.<List<String>>findAnnotationValue(annotation.values, "method")
                .flatMap(value -> {
                    for (String target : value.get()) {
                        String remappedTarget = remaper.remap(owner, target);
                        // Remove owner class; it is always the same as the mixin target
                        remappedTarget = remappedTarget.replaceAll(OWNER_PREFIX, "");
                        int targetDescIndex = remappedTarget.indexOf('(');
                        String targetName = targetDescIndex == -1 ? remappedTarget : remappedTarget.substring(0, targetDescIndex);
                        String targetDesc = targetDescIndex == -1 ? null : remappedTarget.substring(targetDescIndex);
                        if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(targetName, targetDesc))) {
                            Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
                            map.put("method", value);
                            if (annotation.desc.equals(Patch.MODIFY_ARG) || annotation.desc.equals(Patch.MODIFY_VAR)) {
                                map.put("index", PatchInstance.<Integer>findAnnotationValue(annotation.values, "index").orElse(null));
                            }
                            if (!this.targetInjectionPoints.isEmpty()) {
                                Map<String, AnnotationValueHandle<?>> injectCheck = checkInjectionPoint(owner, annotation, remaper).orElse(null);
                                if (injectCheck != null) {
                                    map.putAll(injectCheck);
                                    return Optional.of(map);
                                }
                            } else {
                                return Optional.of(map);
                            }
                        }
                    }
                    return Optional.empty();
                });
        }
        return Optional.empty();
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkInjectionPoint(String owner, AnnotationNode annotation, PatchEnvironment remaper) {
        return PatchInstance.findAnnotationValue(annotation.values, "at")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .flatMap(node -> checkAtAnnotation(owner, node, remaper))
            // Check slice.from target
            .or(() -> PatchInstance.<AnnotationNode>findAnnotationValue(annotation.values, "slice")
                .flatMap(slice -> slice.findNested("from")
                    .flatMap(from -> checkAtAnnotation(owner, from.get(), remaper))));
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkAtAnnotation(String owner, AnnotationNode annotation, PatchEnvironment remaper) {
        AnnotationValueHandle<String> value = PatchInstance.<String>findAnnotationValue(annotation.values, "value").orElse(null);
        String valueStr = value != null ? value.get() : null;
        AnnotationValueHandle<String> target = PatchInstance.<String>findAnnotationValue(annotation.values, "target").orElse(null);
        String targetStr = target != null ? remaper.remap(owner, target.get()) : "";
        if (this.targetInjectionPoints.stream().anyMatch(pred -> pred.test(valueStr, targetStr))) {
            Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
            map.put("value", value);
            map.put("target", target);
            return Optional.of(map);
        }
        return Optional.empty();
    }

    protected static class ClassPatchBuilderImpl extends BaseBuilder<ClassPatchBuilder> implements ClassPatchBuilder {
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
        public ClassPatchBuilder modifyInjectionPoint(String value, String target) {
            return transform(new ModifyInjectionPoint(value, target));
        }

        @Override
        public ClassPatchBuilder redirectShadowMethod(String original, String target, BiConsumer<MethodInsnNode, InsnList> callFixer) {
            return transform(new RedirectShadowMethod(original, target, callFixer));
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
