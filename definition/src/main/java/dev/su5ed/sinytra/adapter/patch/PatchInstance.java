package dev.su5ed.sinytra.adapter.patch;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.transformer.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract sealed class PatchInstance implements Patch permits ClassPatchInstance, InterfacePatchInstance {
    public static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final Collection<String> KNOWN_MIXIN_TYPES = Set.of(Patch.INJECT, Patch.REDIRECT, Patch.MODIFY_ARG, Patch.MODIFY_VAR, Patch.MODIFY_CONST, Patch.MODIFY_EXPR_VAL, Patch.WRAP_OPERATION);

    public static final Marker MIXINPATCH = MarkerFactory.getMarker("MIXINPATCH");

    protected final List<String> targetClasses;

    protected final List<String> targetAnnotations;
    @Nullable
    protected final Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
    protected final List<ClassTransform> classTransforms;
    protected final List<MethodTransform> transforms;

    protected PatchInstance(List<String> targetClasses, List<String> targetAnnotations, List<MethodTransform> transforms) {
        this(targetClasses, targetAnnotations, map -> true, List.of(), transforms);
    }

    protected PatchInstance(List<String> targetClasses, List<String> targetAnnotations, Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        this.targetClasses = targetClasses;
        this.targetAnnotations = targetAnnotations;
        this.targetAnnotationValues = targetAnnotationValues;
        this.classTransforms = classTransforms;
        this.transforms = transforms;
    }

    public abstract Codec<? extends PatchInstance> codec();

    @Override
    public Result apply(ClassNode classNode, PatchEnvironment environment) {
        Result result = Result.PASS;
        PatchContext context = new PatchContext(classNode, environment);
        Pair<Boolean, @Nullable AnnotationValueHandle<?>> classTarget = checkClassTarget(classNode, this.targetClasses);
        if (classTarget.getFirst()) {
            AnnotationValueHandle<?> classAnnotation = classTarget.getSecond();
            for (ClassTransform classTransform : this.classTransforms) {
                result = result.or(classTransform.apply(classNode));
            }
            for (MethodNode method : classNode.methods) {
                Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>> annotationValues = checkMethodTarget(classNode.name, method, environment).orElse(null);
                if (annotationValues != null) {
                    Map<String, AnnotationValueHandle<?>> map = new HashMap<>(annotationValues.getSecond());
                    if (classAnnotation != null) {
                        map.put("class_target", classAnnotation);
                    }
                    for (MethodTransform transform : this.transforms) {
                        AnnotationNode annotation = annotationValues.getFirst();
                        Collection<String> accepted = transform.getAcceptedAnnotations();
                        if (accepted.isEmpty() || accepted.contains(annotation.desc)) {
                            result = result.or(transform.apply(classNode, method, annotationValues.getFirst(), map, context));
                        }
                    }
                }
            }
            context.run();
        }
        return result;
    }

    private static Pair<Boolean, @Nullable AnnotationValueHandle<?>> checkClassTarget(ClassNode classNode, Collection<String> targets) {
        if (targets.isEmpty()) {
            return Pair.of(true, null);
        } else if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MIXIN_ANN)) {
                    return PatchInstance.<List<Type>>findAnnotationValue(annotation.values, "value")
                        .<Pair<Boolean, AnnotationValueHandle<?>>>map(types -> {
                            for (Type targetType : types.get()) {
                                if (targets.contains(targetType.getInternalName())) {
                                    return Pair.of(true, types);
                                }
                            }
                            return null;
                        })
                        .or(() -> PatchInstance.<List<String>>findAnnotationValue(annotation.values, "targets")
                            .map(types -> {
                                for (String targetType : types.get()) {
                                    if (targets.contains(targetType)) {
                                        return Pair.of(true, types);
                                    }
                                }
                                return null;
                            }))
                        .orElse(Pair.of(false, null));
                }
            }
        }
        return Pair.of(false, null);
    }

    private Optional<Pair<AnnotationNode, Map<String, AnnotationValueHandle<?>>>> checkMethodTarget(String owner, MethodNode method, PatchEnvironment remaper) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (this.targetAnnotations.isEmpty() || this.targetAnnotations.contains(annotation.desc)) {
                    Map<String, AnnotationValueHandle<?>> values = checkAnnotation(owner, method, annotation, remaper).orElse(null);
                    if (values != null && (this.targetAnnotationValues == null || this.targetAnnotationValues.test(values))) {
                        return Optional.of(Pair.of(annotation, values));
                    }
                }
            }
        }
        return Optional.empty();
    }

    protected abstract Optional<Map<String, AnnotationValueHandle<?>>> checkAnnotation(String owner, MethodNode method, AnnotationNode annotation, PatchEnvironment remaper);

    public static <T> Optional<AnnotationValueHandle<T>> findAnnotationValue(@Nullable List<Object> values, String key) {
        if (values != null) {
            for (int i = 0; i < values.size(); i += 2) {
                String atKey = (String) values.get(i);
                if (atKey.equals(key)) {
                    int index = i + 1;
                    return Optional.of(new AnnotationValueHandle<>(values, index, key));
                }
            }
        }
        return Optional.empty();
    }

    protected abstract static class BaseBuilder<T extends Builder<T>> implements Builder<T> {
        protected final Set<String> targetClasses = new HashSet<>();
        protected final Set<String> targetAnnotations = new HashSet<>();
        protected Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues;
        protected final List<ClassTransform> classTransforms = new ArrayList<>();
        protected final List<MethodTransform> transforms = new ArrayList<>();

        @Override
        public T targetClass(String... targets) {
            this.targetClasses.addAll(List.of(targets));
            return coerce();
        }

        @Override
        public T targetMixinType(String annotationDesc) {
            this.targetAnnotations.add(annotationDesc);
            return coerce();
        }

        @Override
        public T targetAnnotationValues(Predicate<Map<String, AnnotationValueHandle<?>>> values) {
            this.targetAnnotationValues = this.targetAnnotationValues == null ? values : this.targetAnnotationValues.or(values);
            return coerce();
        }

        @Override
        public T modifyTargetClasses(Consumer<List<Type>> consumer) {
            return transform(new ModifyTargetClasses(consumer));
        }

        @Override
        public T modifyParams(Consumer<ModifyMethodParams.Builder> consumer) {
            ModifyMethodParams.Builder builder = ModifyMethodParams.builder();
            consumer.accept(builder);
            return transform(builder.build());
        }

        @Override
        public T modifyTarget(String... methods) {
            return transform(new ModifyInjectionTarget(List.of(methods)));
        }

        @Override
        public T modifyVariableIndex(int start, int offset) {
            return transform(new ChangeModifiedVariableIndex(start, offset));
        }

        @Override
        public T modifyMethodAccess(ModifyMethodAccess.AccessChange... changes) {
            return transform(new ModifyMethodAccess(List.of(changes)));
        }

        @Override
        public T modifyAnnotationValues(Predicate<AnnotationNode> annotation) {
            return transform(new ModifyAnnotationValues(annotation));
        }

        @Override
        public T extractMixin(String targetClass) {
            return transform(new ExtractMixin(targetClass));
        }

        @Override
        public T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer) {
            return transform(new ModifyMixinType(newType, consumer));
        }

        @Override
        public T transform(ClassTransform transformer) {
            this.classTransforms.add(transformer);
            return coerce();
        }

        @Override
        public T transform(MethodTransform transformer) {
            this.transforms.add(transformer);
            return coerce();
        }

        @SuppressWarnings("unchecked")
        private T coerce() {
            return (T) this;
        }
    }
}
