package org.sinytra.adapter.patch;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.util.Locals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public record MethodContextImpl(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, @Nullable AnnotationHandle injectionPointAnnotation,
                                List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) implements MethodContext {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MethodContextImpl(AnnotationValueHandle<?> classAnnotation, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) {
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
        this.matchingTargets = Objects.requireNonNull(matchingTargets, "Missing matching targets");
        this.patchContext = patchContext;
    }

    @Override
    public AnnotationHandle injectionPointAnnotationOrThrow() {
        return Objects.requireNonNull(this.injectionPointAnnotation, "Missing injection point annotation");
    }

    @Override
    public TargetPair findCleanInjectionTarget() {
        ClassLookup cleanClassLookup = this.patchContext.environment().cleanClassLookup();
        return findInjectionTarget(this.patchContext, s -> cleanClassLookup.getClass(s).orElse(null));
    }

    @Override
    public TargetPair findDirtyInjectionTarget() {
        return findInjectionTarget(this.patchContext, AdapterUtil::getClassNode);
    }

    @Nullable
    @Override
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
    @Override
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

    @Override
    public List<AbstractInsnNode> findInjectionTargetInsns(ClassNode classNode, ClassNode targetClass, MethodNode methodNode, MethodNode targetMethod, PatchContext context) {
        AnnotationHandle atNode = injectionPointAnnotation();
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            return List.of();
        }
        AnnotationHandle annotation = methodAnnotation();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.environment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, methodNode, annotation.unwrap(), atNode.unwrap());
        // Find target instructions
        InsnList instructions = getSlicedInsns(annotation, classNode, methodNode, targetClass, targetMethod, context);
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            injectionPoint.find(targetMethod.desc, instructions, targetInsns);
        } catch (InvalidInjectionException | UnsupportedOperationException e) {
            LOGGER.error("Error finding injection insns: {}", e.getMessage());
            return List.of();
        }
        return targetInsns;
    }

    @Override
    public void updateDescription(ClassNode classNode, MethodNode methodNode, List<Type> parameters) {
        Type returnType = Type.getReturnType(methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        LOGGER.info(PatchInstance.MIXINPATCH, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
        methodNode.desc = newDesc;
        methodNode.signature = null;
    }

    @Override
    public boolean isStatic(MethodNode methodNode) {
        return (methodNode.access & Opcodes.ACC_STATIC) != 0;
    }

    @Override
    public @Nullable List<LocalVariable> getTargetMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod) {
        Type[] targetParams = Type.getArgumentTypes(targetMethod.desc);
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
        int lvtOffset = isStatic ? 0 : 1;
        // The starting LVT index is of the first var after all method parameters. Offset by 1 for instance methods to skip 'this'
        int targetLocalPos = targetParams.length + lvtOffset;
        return getTargetMethodLocals(classNode, methodNode, targetClass, targetMethod, targetLocalPos);
    }

    @Nullable
    public List<LocalVariable> getTargetMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod, int startPos) {
        List<AbstractInsnNode> targetInsns = findInjectionTargetInsns(classNode, targetClass, methodNode, targetMethod, patchContext());
        if (targetInsns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }
        // Get available local variables at the injection point in the target method
        LocalVariableNode[] localVariables;
        // Synchronize to avoid issues in mixin. This is necessary.
        synchronized (this) {
            localVariables = Locals.getLocalsAt(targetClass, targetMethod, targetInsns.get(0), patchContext().environment().fabricLVTCompatibility());
        }
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return AdapterUtil.summariseLocals(locals, startPos);
    }

    private InsnList getSlicedInsns(AnnotationHandle parentAnnotation, ClassNode classNode, MethodNode injectorMethod, ClassNode targetClass, MethodNode targetMethod, PatchContext context) {
        return parentAnnotation.<AnnotationNode>getValue("slice")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .map(sliceAnn -> {
                IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.environment());
                ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, injectorMethod);
                return computeSlicedInsns(sliceContext, sliceAnn, targetMethod);
            })
            .orElse(targetMethod.instructions);
    }

    private InsnList computeSlicedInsns(ISliceContext context, AnnotationNode annotation, MethodNode method) {
        MethodSlice slice = MethodSlice.parse(context, annotation);
        return slice.getSlice(method);
    }

    @Nullable
    private TargetPair findInjectionTarget(PatchContext context, Function<String, ClassNode> classLookup) {
        // Find target method qualifier
        MethodQualifier qualifier = getTargetMethodQualifier(context);
        if (qualifier == null || qualifier.name() == null || qualifier.desc() == null) {
            return null;
        }
        String owner = Optional.ofNullable(qualifier.internalOwnerName())
            .orElseGet(() -> {
                List<Type> targetTypes = targetTypes();
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
        return new TargetPair(targetClass, targetMethod);
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

        public MethodContextImpl build(PatchContext context) {
            return new MethodContextImpl(this.classAnnotation, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes), List.copyOf(this.matchingTargets), context);
        }
    }
}
