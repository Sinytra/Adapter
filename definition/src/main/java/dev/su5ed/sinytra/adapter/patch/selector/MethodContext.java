package dev.su5ed.sinytra.adapter.patch.selector;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.MockMixinRuntime;
import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, @Nullable AnnotationHandle injectionPointAnnotation,
                            List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MethodContext(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) {
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
        this.matchingTargets = Objects.requireNonNull(matchingTargets, "Missing matching targets");
        this.patchContext = patchContext;
    }

    public AnnotationHandle injectionPointAnnotationOrThrow() {
        return Objects.requireNonNull(injectionPointAnnotation, "Missing injection point annotation");
    }

    @SuppressWarnings("unchecked")
    public List<Type> getTargetClasses() {
        if (this.classAnnotation.getKey().equals("value")) {
            return (List<Type>) this.classAnnotation.get();
        }
        return List.of();
    }

    public Pair<ClassNode, MethodNode> findCleanInjectionTarget() {
        ClassLookup cleanClassLookup = this.patchContext.getEnvironment().getCleanClassLookup();
        return findInjectionTarget(this.patchContext, s -> cleanClassLookup.getClass(s).orElse(null));
    }

    public Pair<ClassNode, MethodNode> findDirtyInjectionTarget() {
        return findInjectionTarget(this.patchContext, AdapterUtil::getClassNode);
    }

    @Nullable
    public Pair<ClassNode, MethodNode> findInjectionTarget(PatchContext context, Function<String, ClassNode> classLookup) {
        // Find target method qualifier
        MethodQualifier qualifier = getTargetMethodQualifier(context);
        if (qualifier == null || qualifier.name() == null || qualifier.desc() == null) {
            return null;
        }
        String owner = Optional.ofNullable(qualifier.internalOwnerName())
            .orElseGet(() -> {
                List<Type> targetTypes = getTargetClasses();
                if (targetTypes.size() == 1) {
                    return targetTypes.get(0).getInternalName();
                }
                return null;
            });
        if (owner == null) {
            return null;
        }
        // Find target class
        ClassNode targetClass = classLookup.apply(owner);
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
    public MethodQualifier getTargetMethodQualifier(PatchContext context) {
        // Get method targets
        List<String> methodRefs = methodAnnotation().<List<String>>getValue("method").orElseThrow().get();
        if (methodRefs.size() > 1) {
            // We only support single method targets for now
            return null;
        }
        // Resolve method reference
        String reference = context.remap(methodRefs.get(0));
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference, false).orElse(null);
    }

    @Nullable
    public MethodQualifier getInjectionPointMethodQualifier(PatchContext context) {
        // Get injection target
        String target = injectionPointAnnotation().<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
        if (target == null) {
            return null;
        }
        // Resolve method reference
        String reference = context.remap(target);
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference, false).orElse(null);
    }

    public List<AbstractInsnNode> findInjectionTargetInsns(ClassNode classNode, ClassNode targetClass, MethodNode methodNode, MethodNode targetMethod, PatchContext context) {
        AnnotationHandle atNode = injectionPointAnnotation();
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            return null;
        }
        AnnotationHandle annotation = methodAnnotation();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.getEnvironment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, methodNode, annotation.unwrap(), atNode.unwrap());
        // Find target instructions
        InsnList instructions = getSlicedInsns(annotation, classNode, methodNode, targetClass, targetMethod, context);
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            injectionPoint.find(targetMethod.desc, instructions, targetInsns);
        } catch (InvalidInjectionException | UnsupportedOperationException e) {
            LOGGER.error("Error finding injection insns: {}", e.getMessage());
            return null;
        }
        return targetInsns;
    }

    private InsnList getSlicedInsns(AnnotationHandle parentAnnotation, ClassNode classNode, MethodNode injectorMethod, ClassNode targetClass, MethodNode targetMethod, PatchContext context) {
        return parentAnnotation.<AnnotationNode>getValue("slice")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .map(sliceAnn -> {
                IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.getEnvironment());
                ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, injectorMethod);
                return computeSlicedInsns(sliceContext, sliceAnn, targetMethod);
            })
            .orElse(targetMethod.instructions);
    }

    private InsnList computeSlicedInsns(ISliceContext context, AnnotationNode annotation, MethodNode method) {
        MethodSlice slice = MethodSlice.parse(context, annotation);
        return slice.getSlice(method);
    }

    public void updateDescription(ClassNode classNode, MethodNode methodNode, List<Type> parameters) {
        Type returnType = Type.getReturnType(methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        LOGGER.info(MIXINPATCH, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
        methodNode.desc = newDesc;
        methodNode.signature = null;
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

        public MethodContext build(PatchContext context) {
            return new MethodContext(this.classAnnotation, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes), List.copyOf(this.matchingTargets), context);
        }
    }
}
