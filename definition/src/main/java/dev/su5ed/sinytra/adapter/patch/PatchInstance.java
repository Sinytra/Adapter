package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
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
    public static final Collection<String> KNOWN_MIXIN_TYPES = Set.of(MixinConstants.INJECT, MixinConstants.REDIRECT, MixinConstants.MODIFY_ARG, MixinConstants.MODIFY_ARGS, MixinConstants.MODIFY_VAR, MixinConstants.MODIFY_CONST, MixinConstants.MODIFY_EXPR_VAL, MixinConstants.WRAP_OPERATION);

    public static final Marker MIXINPATCH = MarkerFactory.getMarker("MIXINPATCH");

    protected final List<String> targetClasses;

    protected final List<String> targetAnnotations;
    @Nullable
    protected final Predicate<AnnotationHandle> targetAnnotationValues;
    protected final List<ClassTransform> classTransforms;
    protected final List<MethodTransform> transforms;

    protected PatchInstance(List<String> targetClasses, List<String> targetAnnotations, List<MethodTransform> transforms) {
        this(targetClasses, targetAnnotations, map -> true, List.of(), transforms);
    }

    protected PatchInstance(List<String> targetClasses, List<String> targetAnnotations, Predicate<AnnotationHandle> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
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
        ClassTarget classTarget = checkClassTarget(classNode);
        if (classTarget != null) {
            PatchContextImpl context = new PatchContextImpl(classNode, classTarget.targetTypes(), environment);
            AnnotationValueHandle<?> classAnnotation = classTarget.handle();
            for (ClassTransform classTransform : this.classTransforms) {
                result = result.or(classTransform.apply(classNode, classTarget.handle(), context));
            }
            for (MethodNode method : classNode.methods) {
                MethodContext methodContext = checkMethodTarget(classAnnotation, classNode.name, method, environment, classTarget.targetTypes(), context);
                if (methodContext != null) {
                    for (MethodTransform transform : this.transforms) {
                        Collection<String> accepted = transform.getAcceptedAnnotations();
                        if (accepted.isEmpty() || accepted.contains(methodContext.methodAnnotation().getDesc())) {
                            result = result.or(transform.apply(classNode, method, methodContext, context));
                        }
                    }
                }
            }
            context.run();
        }
        return result;
    }

    private ClassTarget checkClassTarget(ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MixinConstants.MIXIN)) {
                    return PatchInstance.<List<Type>>findAnnotationValue(annotation.values, "value")
                        .map(types -> {
                            for (Type targetType : types.get()) {
                                if (this.targetClasses.isEmpty() || this.targetClasses.contains(targetType.getInternalName())) {
                                    return new ClassTarget(types, types.get());
                                }
                            }
                            return null;
                        })
                        .or(() -> PatchInstance.<List<String>>findAnnotationValue(annotation.values, "targets")
                            .map(types -> {
                                for (String targetType : types.get()) {
                                    if (this.targetClasses.isEmpty() || this.targetClasses.contains(targetType)) {
                                        return new ClassTarget(types, types.get().stream().map(Type::getObjectType).toList());
                                    }
                                }
                                return null;
                            }))
                        .orElse(null);
                }
            }
        }
        return this.targetClasses.isEmpty() ? new ClassTarget(null, List.of()) : null;
    }

    @Nullable
    private MethodContext checkMethodTarget(@Nullable AnnotationValueHandle<?> classAnnotation, String owner, MethodNode method, PatchEnvironment remaper, List<Type> targetTypes, PatchContext context) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (this.targetAnnotations.isEmpty() || this.targetAnnotations.contains(annotation.desc)) {
                    MethodContextImpl.Builder builder = MethodContextImpl.builder();
                    if (classAnnotation != null) {
                        builder.classAnnotation(classAnnotation);
                        builder.targetTypes(targetTypes);
                    }
                    AnnotationHandle annotationHandle = new AnnotationHandle(annotation);
                    if (checkAnnotation(owner, method, annotationHandle, remaper, builder) && (this.targetAnnotationValues == null || this.targetAnnotationValues.test(annotationHandle))) {
                        return builder.build(context);
                    }
                }
            }
        }
        return null;
    }

    protected abstract boolean checkAnnotation(String owner, MethodNode method, AnnotationHandle annotation, PatchEnvironment remaper, MethodContextImpl.Builder builder);

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

    private record ClassTarget(@Nullable AnnotationValueHandle<?> handle, List<Type> targetTypes) {}

    protected abstract static class BaseBuilder<T extends Builder<T>> implements Builder<T> {
        protected final Set<String> targetClasses = new HashSet<>();
        protected final Set<String> targetAnnotations = new HashSet<>();
        protected Predicate<AnnotationHandle> targetAnnotationValues;
        protected final List<ClassTransform> classTransforms = new ArrayList<>();
        protected final List<MethodTransform> transforms = new ArrayList<>();

        @Override
        public T targetClass(String... targets) {
            this.targetClasses.addAll(List.of(targets));
            return coerce();
        }

        @Override
        public T targetMixinType(String... annotationDescs) {
            this.targetAnnotations.addAll(List.of(annotationDescs));
            return coerce();
        }

        @Override
        public T targetAnnotationValues(Predicate<AnnotationHandle> values) {
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
        public T modifyTarget(ModifyInjectionTarget.Action action, String... methods) {
            return transform(new ModifyInjectionTarget(List.of(methods), action));
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
        public T extractMixin(String targetClass) {
            return transform(new ExtractMixin(targetClass));
        }

        @Override
        public T improveModifyVar() {
            return transform(ModifyVarUpgradeToModifyExprVal.INSTANCE);
        }

        @Override
        public T modifyMixinType(String newType, Consumer<ModifyMixinType.Builder> consumer) {
            return transform(new ModifyMixinType(newType, consumer));
        }

        @Override
        public T transform(List<ClassTransform> classTransforms) {
            this.classTransforms.addAll(classTransforms);
            return coerce();
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

        @Override
        public T chain(Consumer<T> consumer) {
            consumer.accept(coerce());
            return coerce();
        }

        @SuppressWarnings("unchecked")
        private T coerce() {
            return (T) this;
        }
    }
}
