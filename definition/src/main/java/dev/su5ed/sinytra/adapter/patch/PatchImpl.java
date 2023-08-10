package dev.su5ed.sinytra.adapter.patch;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.transformer.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class PatchImpl implements Patch {
    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final String INJECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    public static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    public static final String OVERWRITE_ANN = "Lorg/spongepowered/asm/mixin/Overwrite;";
    public static final String MODIFY_VARIABLE_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    public static final String MODIFY_ARG_ANN = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String OWNER_PREFIX = "^(?<owner>L(?:.*?)+;)";

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker PATCHER = MarkerFactory.getMarker("MIXINPATCH");
    public static final Codec<PatchImpl> CODEC = RecordCodecBuilder
        .<PatchImpl>create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("targetClasses", List.of()).forGetter(p -> p.targetClasses),
            MethodMatcher.CODEC.listOf().optionalFieldOf("targetMethods", List.of()).forGetter(p -> p.targetMethods),
            InjectionPointMatcher.CODEC.listOf().optionalFieldOf("targetInjectionPoints", List.of()).forGetter(p -> p.targetInjectionPoints),
            Codec.STRING.listOf().optionalFieldOf("targetAnnotations", List.of()).forGetter(p -> p.targetAnnotations),
            PatchSerialization.METHOD_TRANSFORM_CODEC.listOf().fieldOf("transforms").forGetter(p -> p.transforms)
        ).apply(instance, PatchImpl::new))
        .comapFlatMap(obj -> obj.targetAnnotationValues != null ? DataResult.error(() -> "Cannot serialize targetAnnotationValues") : DataResult.success(obj), Function.identity());

    private final List<String> targetClasses;
    private final List<MethodMatcher> targetMethods;
    private final List<InjectionPointMatcher> targetInjectionPoints;
    private final List<String> targetAnnotations;
    @Nullable
    private final Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
    private final List<MethodTransform> transforms;

    private PatchImpl(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, List<MethodTransform> transforms) {
        this(targetClasses, targetMethods, targetInjectionPoints, targetAnnotations, map -> true, transforms);
    }

    private PatchImpl(List<String> targetClasses, List<MethodMatcher> targetMethods, List<InjectionPointMatcher> targetInjectionPoints, List<String> targetAnnotations, Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues, List<MethodTransform> transforms) {
        this.targetClasses = targetClasses;
        this.targetMethods = targetMethods;
        this.targetInjectionPoints = targetInjectionPoints;
        this.targetAnnotations = targetAnnotations;
        this.targetAnnotationValues = targetAnnotationValues;
        this.transforms = transforms;
    }

    @Override
    public boolean apply(ClassNode classNode) {
        boolean applied = false;
        PatchContext context = new PatchContext();
        if (checkClassTarget(classNode, this.targetClasses)) {
            for (MethodTransform transform : this.transforms) {
                applied |= transform.apply(classNode).applied();
            }
            for (MethodNode method : classNode.methods) {
                Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>> annotationValues = checkMethodTarget(method).orElse(null);
                if (annotationValues != null) {
                    for (MethodTransform transform : this.transforms) {
                        AnnotationNode annotation = annotationValues.getFirst();
                        if (transform.getAcceptedAnnotations().contains(annotation.desc)) {
                            applied |= transform.apply(classNode, method, annotationValues.getFirst(), annotationValues.getSecond(), context);
                        }
                    }
                }
            }
            context.run();
        }
        return applied;
    }

    private record InjectionPointMatcher(@Nullable String value, String target) {
        public static final Codec<InjectionPointMatcher> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("value", null).forGetter(InjectionPointMatcher::value),
            Codec.STRING.fieldOf("target").forGetter(InjectionPointMatcher::target)
        ).apply(instance, InjectionPointMatcher::new));

        public boolean test(String value, String target) {
            return this.target.equals(target) && (this.value == null || this.value.equals(value));
        }
    }

    private static boolean checkClassTarget(ClassNode classNode, Collection<String> targets) {
        if (targets.isEmpty()) {
            return true;
        } else if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MIXIN_ANN)) {
                    return PatchImpl.<List<Type>>findAnnotationValue(annotation.values, "value")
                        .flatMap(types -> {
                            for (Type targetType : types.get()) {
                                if (targets.contains(targetType.getInternalName())) {
                                    return Optional.of(true);
                                }
                            }
                            return Optional.empty();
                        })
                        .or(() -> PatchImpl.<List<String>>findAnnotationValue(annotation.values, "targets")
                            .map(types -> {
                                for (String targetType : types.get()) {
                                    if (targets.contains(targetType)) {
                                        return true;
                                    }
                                }
                                return false;
                            }))
                        .orElse(false);
                }
            }
        }
        return false;
    }

    private Optional<Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>>> checkMethodTarget(MethodNode method) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (this.targetAnnotations.contains(annotation.desc)) {
                    Map<String, AnnotationValueHandle<?>> values = checkAnnotation(method, annotation).orElse(null);
                    if (values != null && (this.targetAnnotationValues == null || this.targetAnnotationValues.test(values))) {
                        return Optional.of(Pair.of(annotation, values));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Map<String, AnnotationValueHandle<?>>> checkAnnotation(MethodNode method, AnnotationNode annotation) {
        if (annotation.desc.equals(OVERWRITE_ANN)) {
            if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(method.name, method.desc))) {
                return Optional.of(Map.of());
            }
        } else if (annotation.desc.equals(INJECT_ANN) || annotation.desc.equals(REDIRECT_ANN) || annotation.desc.equals(MODIFY_VARIABLE_ANN) || annotation.desc.equals(MODIFY_ARG_ANN)) {
            return PatchImpl.<List<String>>findAnnotationValue(annotation.values, "method")
                .flatMap(value -> {
                    for (String target : value.get()) {
                        // Remove owner class; it is always the same as the mixin target
                        target = target.replaceAll(OWNER_PREFIX, "");
                        int targetDescIndex = target.indexOf('(');
                        String targetName = targetDescIndex == -1 ? target : target.substring(0, targetDescIndex);
                        String targetDesc = targetDescIndex == -1 ? null : target.substring(targetDescIndex);
                        if (this.targetMethods.isEmpty() || this.targetMethods.stream().anyMatch(matcher -> matcher.matches(targetName, targetDesc))) {
                            Map<String, AnnotationValueHandle<?>> map = new HashMap<>();
                            map.put("method", value);
                            if (annotation.desc.equals(MODIFY_ARG_ANN)) {
                                map.put("index", PatchImpl.<Integer>findAnnotationValue(annotation.values, "index").orElse(null));
                            }
                            if (!this.targetInjectionPoints.isEmpty()) {
                                Map<String, AnnotationValueHandle<?>> injectCheck = checkInjectionPoint(annotation).orElse(null);
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

    private Optional<Map<String, AnnotationValueHandle<?>>> checkInjectionPoint(AnnotationNode annotation) {
        return PatchImpl.findAnnotationValue(annotation.values, "at")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .flatMap(node -> {
                AnnotationValueHandle<String> value = PatchImpl.<String>findAnnotationValue(node.values, "value").orElse(null);
                String valueStr = value != null ? value.get() : null;
                AnnotationValueHandle<String> target = PatchImpl.<String>findAnnotationValue(node.values, "target").orElse(null);
                if (target != null && this.targetInjectionPoints.stream().anyMatch(pred -> pred.test(valueStr, target.get()))) {
                    return Optional.of(Map.of(
                        "value", value,
                        "target", target
                    ));
                }
                return Optional.empty();
            });
    }

    public static <T> Optional<AnnotationValueHandle<T>> findAnnotationValue(@Nullable List<Object> values, String key) {
        if (values != null) {
            for (int i = 0; i < values.size(); i += 2) {
                String atKey = (String) values.get(i);
                if (atKey.equals(key)) {
                    int index = i + 1;
                    return Optional.of(new AnnotationValueHandle<>(values, index));
                }
            }
        }
        return Optional.empty();
    }

    static class MethodMatcher {
        public static final Codec<MethodMatcher> CODEC = Codec.STRING.xmap(MethodMatcher::new, matcher -> matcher.name + Objects.requireNonNullElse(matcher.desc, ""));

        private final String name;
        @Nullable
        private final String desc;

        public MethodMatcher(String method) {
            int descIndex = method.indexOf('(');
            this.name = descIndex == -1 ? method : method.substring(0, descIndex);
            this.desc = descIndex == -1 ? null : method.substring(descIndex);
        }

        public boolean matches(String name, String desc) {
            return this.name.equals(name) && (this.desc == null || this.desc.equals(desc));
        }
    }

    static class BuilderImpl implements Builder {
        private final Set<String> targetClasses = new HashSet<>();
        private final Set<MethodMatcher> targetMethods = new HashSet<>();
        private final Set<String> targetAnnotations = new HashSet<>();
        private Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
        private final Set<InjectionPointMatcher> targetInjectionPoints = new HashSet<>();
        private final List<MethodTransform> transforms = new ArrayList<>();

        @Override
        public Builder targetClass(String... targets) {
            this.targetClasses.addAll(List.of(targets));
            return this;
        }

        @Override
        public Builder targetMethod(String... targets) {
            for (String target : targets) {
                this.targetMethods.add(new MethodMatcher(target));
            }
            return this;
        }

        @Override
        public Builder targetMixinType(String annotationDesc) {
            this.targetAnnotations.add(annotationDesc);
            return this;
        }

        @Override
        public Builder targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values) {
            this.targetAnnotationValues = this.targetAnnotationValues == null ? values : this.targetAnnotationValues.or(values);
            return this;
        }

        @Override
        public Builder targetInjectionPoint(String target) {
            return targetInjectionPoint(null, target);
        }

        @Override
        public Builder targetInjectionPoint(String value, String target) {
            this.targetInjectionPoints.add(new InjectionPointMatcher(value, target));
            return this;
        }

        @Override
        public Builder modifyInjectionPoint(String target) {
            return modifyInjectionPoint(null, target);
        }

        @Override
        public Builder modifyInjectionPoint(String value, String target) {
            return transform(new ModifyInjectionPoint(value, target));
        }

        @Override
        public Builder modifyParams(List<Type> replacementTypes) {
            return modifyParams(replacementTypes, null);
        }

        @Override
        public Builder modifyParams(List<Type> replacementTypes, @Nullable LVTFixer lvtFixer) {
            return transform(new ModifyMethodParams(replacementTypes, lvtFixer));
        }

        @Override
        public Builder modifyTarget(String... methods) {
            return transform(new ModifyInjectionTarget(List.of(methods)));
        }

        @Override
        public Builder modifyVariableIndex(int start, int offset) {
            return transform(new ChangeModifiedVariableIndex(start, offset));
        }

        @Override
        public Builder disable() {
            return transform(DisableMixin.INSTANCE);
        }

        @Override
        public Builder transform(MethodTransform transformer) {
            this.transforms.add(transformer);
            return this;
        }

        @Override
        public Patch build() {
            return new PatchImpl(
                List.copyOf(this.targetClasses),
                List.copyOf(this.targetMethods),
                List.copyOf(this.targetInjectionPoints),
                List.copyOf(this.targetAnnotations),
                this.targetAnnotationValues,
                List.copyOf(this.transforms)
            );
        }
    }
}
