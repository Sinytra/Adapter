package org.sinytra.adapter.patch;

import com.google.common.base.Suppliers;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
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

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MethodContextImpl implements MethodContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ClassNode classNode;
    private final AnnotationValueHandle<?> classAnnotation;
    private final MethodNode methodNode;
    private final AnnotationHandle methodAnnotation;
    private final @Nullable AnnotationHandle injectionPointAnnotation;
    private final List<Type> targetTypes;
    private final List<String> matchingTargets;
    private final PatchContext patchContext;

    private final Supplier<TargetPair> cleanInjectionPairCache;
    private final Supplier<TargetPair> dirtyInjectionPairCache;
    private final Map<TargetPair, List<AbstractInsnNode>> targetInstructionsCache;

    public MethodContextImpl(ClassNode classNode, AnnotationValueHandle<?> classAnnotation, MethodNode methodNode, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) {
        this.classNode = Objects.requireNonNull(classNode, "Missing class node");
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodNode = Objects.requireNonNull(methodNode, "Missing method node");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
        this.matchingTargets = Objects.requireNonNull(matchingTargets, "Missing matching targets");
        this.patchContext = patchContext;

        this.cleanInjectionPairCache = Suppliers.memoize(() -> {
            ClassLookup cleanClassLookup = this.patchContext.environment().cleanClassLookup();
            return findInjectionTarget(s -> cleanClassLookup.getClass(s).orElse(null));
        });
        this.dirtyInjectionPairCache = Suppliers.memoize(() -> findInjectionTarget(AdapterUtil::getClassNode));
        this.targetInstructionsCache = new HashMap<>();
    }

    @Override
    public AnnotationHandle injectionPointAnnotationOrThrow() {
        return Objects.requireNonNull(this.injectionPointAnnotation, "Missing injection point annotation");
    }

    @Override
    public TargetPair findCleanInjectionTarget() {
        return this.cleanInjectionPairCache.get();
    }

    @Override
    public TargetPair findDirtyInjectionTarget() {
        return this.dirtyInjectionPairCache.get();
    }

    @Nullable
    @Override
    public MethodQualifier getTargetMethodQualifier() {
        // Get method targets
        List<String> methodRefs = methodAnnotation().<List<String>>getValue("method").orElseThrow().get();
        if (methodRefs.size() > 1) {
            // We only support single method targets for now
            return null;
        }
        // Resolve method reference
        String reference = patchContext().remap(methodRefs.get(0));
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference, false).orElse(null);
    }

    @Nullable
    @Override
    public MethodQualifier getInjectionPointMethodQualifier() {
        // Get injection target
        String target = injectionPointAnnotation().<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
        if (target == null) {
            return null;
        }
        // Resolve method reference
        String reference = patchContext().remap(target);
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference, false).orElse(null);
    }

    @Override
    public List<AbstractInsnNode> findInjectionTargetInsns(TargetPair target) {
        return this.targetInstructionsCache.computeIfAbsent(target, this::computeInjectionTargetInsns);
    }

    @Override
    public void updateDescription(List<Type> parameters) {
        Type returnType = Type.getReturnType(this.methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        LOGGER.info(PatchInstance.MIXINPATCH, "Changing descriptor of method {}.{}{} to {}", this.classNode.name, this.methodNode.name, this.methodNode.desc, newDesc);
        this.methodNode.desc = newDesc;
        this.methodNode.signature = null;
    }

    @Override
    public boolean isStatic() {
        return (this.methodNode.access & Opcodes.ACC_STATIC) != 0;
    }

    @Nullable
    @Override
    public List<LocalVariable> getTargetMethodLocals(TargetPair target) {
        Type[] targetParams = Type.getArgumentTypes(target.methodNode().desc);
        boolean isStatic = (this.methodNode.access & Opcodes.ACC_STATIC) != 0;
        int lvtOffset = isStatic ? 0 : 1;
        // The starting LVT index is of the first var after all method parameters. Offset by 1 for instance methods to skip 'this'
        int targetLocalPos = targetParams.length + lvtOffset;
        return getTargetMethodLocals(target, targetLocalPos);
    }

    @Nullable
    @Override
    public List<LocalVariable> getTargetMethodLocals(TargetPair target, int startPos, int lvtCompatLevel) {
        List<AbstractInsnNode> targetInsns = findInjectionTargetInsns(target);
        if (targetInsns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }
        // Get available local variables at the injection point in the target method
        LocalVariableNode[] localVariables;
        // Synchronize to avoid issues in mixin. This is necessary.
        synchronized (this) {
            localVariables = Locals.getLocalsAt(target.classNode(), target.methodNode(), targetInsns.get(0), lvtCompatLevel);
        }
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return AdapterUtil.summariseLocals(locals, startPos);
    }

    private List<AbstractInsnNode> computeInjectionTargetInsns(TargetPair target) {
        AnnotationHandle atNode = injectionPointAnnotation();
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", this.classNode.name, this.methodNode.name, this.methodNode.desc);
            return List.of();
        }
        AnnotationHandle annotation = methodAnnotation();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(this.classNode.name, target.classNode().name, patchContext().environment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, this.methodNode, annotation.unwrap(), atNode.unwrap());
        // Find target instructions
        InsnList instructions = getSlicedInsns(annotation, this.classNode, this.methodNode, target.classNode(), target.methodNode(), patchContext());
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            injectionPoint.find(target.methodNode().desc, instructions, targetInsns);
        } catch (InvalidInjectionException | UnsupportedOperationException e) {
            LOGGER.error("Error finding injection insns: {}", e.getMessage());
            return List.of();
        }
        return targetInsns;
    }

    @Override
    public List<Integer> getLvtCompatLevelsOrdered() {
        int currentLevel = patchContext().environment().fabricLVTCompatibility();
        return MixinConstants.LVT_COMPATIBILITY_LEVELS.stream()
            .sorted(Comparator.comparingInt(i -> i == currentLevel ? 1 : 0))
            .toList();
    }

    @Override
    public boolean capturesLocals() {
        return methodAnnotation().getValue("locals").isPresent();
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
    private TargetPair findInjectionTarget(Function<String, ClassNode> classLookup) {
        // Find target method qualifier
        MethodQualifier qualifier = getTargetMethodQualifier();
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

    @Override
    public AnnotationValueHandle<?> classAnnotation() {
        return this.classAnnotation;
    }

    @Override
    public AnnotationHandle methodAnnotation() {
        return this.methodAnnotation;
    }

    @Override
    @Nullable
    public AnnotationHandle injectionPointAnnotation() {
        return this.injectionPointAnnotation;
    }

    @Override
    public List<Type> targetTypes() {
        return this.targetTypes;
    }

    @Override
    public List<String> matchingTargets() {
        return this.matchingTargets;
    }

    @Override
    public PatchContext patchContext() {
        return this.patchContext;
    }

    public static class Builder {
        private ClassNode classNode;
        private AnnotationValueHandle<?> classAnnotation;
        private MethodNode methodNode;
        private AnnotationHandle methodAnnotation;
        private AnnotationHandle injectionPointAnnotation;
        private final List<Type> targetTypes = new ArrayList<>();
        private final List<String> matchingTargets = new ArrayList<>();

        public Builder classNode(ClassNode classNode) {
            this.classNode = classNode;
            return this;
        }

        public Builder classAnnotation(AnnotationValueHandle<?> annotation) {
            this.classAnnotation = annotation;
            return this;
        }

        public Builder methodNode(MethodNode methodNode) {
            this.methodNode = methodNode;
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
            return new MethodContextImpl(this.classNode, this.classAnnotation, this.methodNode, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes), List.copyOf(this.matchingTargets), context);
        }
    }
}
