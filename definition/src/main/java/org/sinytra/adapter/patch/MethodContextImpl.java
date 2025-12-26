package org.sinytra.adapter.patch;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.util.Locals;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MethodContextImpl implements MethodContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ClassNode classNode;
    private final AnnotationHandle rawClassAnnotation;
    private final AnnotationValueHandle<?> classAnnotation;
    private final MethodNode methodNode;
    private final AnnotationHandle methodAnnotation;
    private final @Nullable AnnotationHandle injectionPointAnnotation;
    private final List<Type> targetTypes;
    private final List<String> matchingTargets;
    private final PatchContext patchContext;

    private final Supplier<TargetPair> cleanInjectionPairCache;
    private final Supplier<TargetPair> dirtyInjectionPairCache;
    private final Supplier<LocalVariableLookup> cleanLocalsTableCache;
    private final Supplier<LocalVariableLookup> dirtyLocalsTableCache;
    private final Map<TargetPair, List<AbstractInsnNode>> targetInstructionsCache;

    public MethodContextImpl(ClassNode classNode, AnnotationHandle rawClassAnnotation, AnnotationValueHandle<?> classAnnotation, MethodNode methodNode, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, List<Type> targetTypes, List<String> matchingTargets, PatchContext patchContext) {
        this.classNode = Objects.requireNonNull(classNode, "Missing class node");
        this.rawClassAnnotation = Objects.requireNonNull(rawClassAnnotation, "Missing raw class annotation");
        this.classAnnotation = Objects.requireNonNull(classAnnotation, "Missing class annotation");
        this.methodNode = Objects.requireNonNull(methodNode, "Missing method node");
        this.methodAnnotation = Objects.requireNonNull(methodAnnotation, "Missing method annotation");
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.targetTypes = Objects.requireNonNull(targetTypes, "Missing target types");
        this.matchingTargets = Objects.requireNonNull(matchingTargets, "Missing matching targets");
        this.patchContext = patchContext;

        this.cleanInjectionPairCache = Suppliers.memoize(() -> {
            ClassLookup cleanClassLookup = this.patchContext.environment().cleanClassLookup();
            return findInjectionTarget(cleanClassLookup);
        });
        this.dirtyInjectionPairCache = Suppliers.memoize(() -> findInjectionTarget(this.patchContext.environment().dirtyClassLookup()));
        this.targetInstructionsCache = new HashMap<>();
        this.cleanLocalsTableCache = Suppliers.memoize(() -> Optional.ofNullable(findCleanInjectionTarget()).map(pair -> new LocalVariableLookup(pair.methodNode())).orElse(null));
        this.dirtyLocalsTableCache = Suppliers.memoize(() -> Optional.ofNullable(findDirtyInjectionTarget()).map(pair -> new LocalVariableLookup(pair.methodNode())).orElse(null));
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

    @Override
    public LocalVariableLookup cleanLocalsTable() {
        return this.cleanLocalsTableCache.get();
    }

    @Override
    public LocalVariableLookup dirtyLocalsTable() {
        return this.dirtyLocalsTableCache.get();
    }

    @Nullable
    @Override
    public MethodQualifier getTargetMethodQualifier() {
        // Get method targets
        List<String> methodRefs = methodAnnotation().<List<String>>getValue("method").map(AnnotationValueHandle::get).orElseGet(Collections::emptyList);
        if (methodRefs.size() != 1) {
            // We only support single method targets for now
            return null;
        }
        // Resolve method reference
        String reference = patchContext().remap(methodRefs.getFirst());
        // Extract owner, name and desc using regex
        return MethodQualifier.create(reference).orElse(null);
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
        return MethodQualifier.create(reference).orElse(null);
    }

    @Override
    public List<AbstractInsnNode> findInjectionTargetInsns(@Nullable TargetPair target) {
        return this.targetInstructionsCache.computeIfAbsent(target, this::computeInjectionTargetInsns);
    }

    @Override
    public void updateDescription(MethodTransform transform, List<Type> parameters) {
        Type returnType = Type.getReturnType(this.methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        recordAudit(transform, "Change descriptor to %s", newDesc);
        this.methodNode.desc = newDesc;
        this.methodNode.signature = null;
    }

    @Override
    public boolean isStatic() {
        return (this.methodNode.access & Opcodes.ACC_STATIC) != 0;
    }

    @Override
    public boolean isCancellable() {
        return methodAnnotation().matchesDesc(MixinConstants.INJECT) && methodAnnotation().<Boolean>getValue("cancellable").map(AnnotationValueHandle::get).orElse(false);
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
            localVariables = Locals.getLocalsAt(target.classNode(), target.methodNode(), targetInsns.getFirst(), lvtCompatLevel);
        }
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return AdapterUtil.summariseLocals(locals, startPos);
    }

    @Nullable
    public List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable TargetPair target) {
        return computeInjectionTargetInsns(target, this::injectionPointAnnotation, (ctx, h) -> InjectionPoint.parse(ctx, this.methodNode, methodAnnotation().unwrap(), h.unwrap()));
    }

    private List<AbstractInsnNode> computeConstantTargetInsns(@Nullable TargetPair target) {
        return computeInjectionTargetInsns(target, () -> methodAnnotation().getNested("constant").orElse(null), (ctx, h) -> new BeforeConstant(ctx, h.unwrap(), Type.getReturnType(this.methodNode.desc).getDescriptor()));
    }

    @Nullable
    private List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable TargetPair target, Supplier<AnnotationHandle> atNodeSupplier, BiFunction<IMixinContext, AnnotationHandle, InjectionPoint> injectionPointParser) {
        if (target == null) {
            return List.of();
        }
        AnnotationHandle atNode = atNodeSupplier.get();
        if (atNode == null) {
            return List.of();
        }
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(this.classNode.name, target.classNode().name, patchContext().environment());
        // Parse injection point
        InjectionPoint injectionPoint = injectionPointParser.apply(mixinContext, atNode);
        Target mixinTarget = MockMixinRuntime.createMixinTarget(target);
        // Find target instructions
        InsnList instructions = getSlicedInsns(methodAnnotation(), this.classNode, this.methodNode, target.classNode(), target.methodNode(), patchContext(), mixinTarget);
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            if (MockMixinRuntime.injectionPointNeedsSpecialCare(injectionPoint)) {
                MockMixinRuntime.findModifyVariableInjectionInsns(injectionPoint, mixinContext, target.methodNode().instructions, targetInsns, mixinTarget);
            } else {
                injectionPoint.find(target.methodNode().desc, instructions, targetInsns);
            }
        } catch (Throwable e) {
            LOGGER.error("Error finding injection insns", e);
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

    @Override
    public boolean failsDirtyInjectionCheck() {
        TargetPair dirtyPair = findDirtyInjectionTarget();
        return dirtyPair == null || computeInjectionTargetInsns(dirtyPair).isEmpty() && computeConstantTargetInsns(dirtyPair).isEmpty();
    }

    @Override
    public boolean hasInjectionPointValue(String value) {
        return this.injectionPointAnnotation != null && this.injectionPointAnnotation.<String>getValue("value").map(v -> value.equals(v.get())).orElse(false);
    }

    @Override
    public boolean isNotRequired() {
        return this.methodAnnotation.<Integer>getValue("require")
            .map(v -> v.get() == 0)
            .orElse(false);
    }

    @Override
    public boolean hasValidSlice(TargetPair target) {
        if (target == null) {
            return false;
        }

        AnnotationHandle ann = this.methodAnnotation.<AnnotationNode>getValue("slice")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) value;
            })
            .map(AnnotationHandle::new)
            .orElse(null);
        if (ann == null) {
            return true;
        }

        AnnotationNode from = ann.getNested("from").map(AnnotationHandle::unwrap).orElse(null);
        if (from != null && !validateAtNode(from, target)) {
            return false;
        }

        AnnotationNode to = ann.getNested("to").map(AnnotationHandle::unwrap).orElse(null);
        return to == null || validateAtNode(to, target);
    }

    private boolean validateAtNode(AnnotationNode at, TargetPair target) {
        List<AbstractInsnNode> insns = computeInjectionTargetInsns(
            target,
            this::injectionPointAnnotation,
            (ctx, h) -> InjectionPoint.parse(ctx, this.methodNode, methodAnnotation().unwrap(), at)
        );
        return !insns.isEmpty();
    }

    @Override
    public void recordAudit(Object transform, String message, Object... args) {
        this.patchContext.environment().auditTrail().recordAudit(transform, this, message, args);
    }

    private InsnList getSlicedInsns(AnnotationHandle parentAnnotation, ClassNode classNode, MethodNode injectorMethod, ClassNode targetClass, MethodNode targetMethod, PatchContext context, Target mixinTarget) {
        return parentAnnotation.<AnnotationNode>getValue("slice")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) value;
            })
            .map(sliceAnn -> {
                IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.environment());
                ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, injectorMethod);
                return computeSlicedInsns(sliceContext, sliceAnn, mixinTarget);
            })
            .orElse(targetMethod.instructions);
    }

    private InsnList computeSlicedInsns(ISliceContext context, AnnotationNode annotation, Target mixinTarget) {
        MethodSlice slice = MethodSlice.parse(context, annotation);
        return slice.getSlice(mixinTarget);
    }

    @Nullable
    private TargetPair findInjectionTarget(ClassLookup lookup) {
        Pair<ClassNode, List<MethodNode>> pair = findInjectionTargetCandidates(lookup, false);
        if (pair == null) {
            return null;
        }

        MethodQualifier qualifier = getTargetMethodQualifier();
        if (pair.getSecond().isEmpty()) {
            LOGGER.debug("Target method not found: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        } else if (pair.getSecond().size() > 1) {
            LOGGER.debug("Multiple candidates found for method: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        }
        return new TargetPair(pair.getFirst(), pair.getSecond().getFirst());
    }

    @Nullable
    public Pair<ClassNode, List<MethodNode>> findInjectionTargetCandidates(ClassLookup lookup, boolean ignoreDesc) {
        // Find target method qualifier
        MethodQualifier qualifier = getTargetMethodQualifier();
        if (qualifier == null || qualifier.name() == null) {
            return null;
        }
        String owner = Optional.ofNullable(qualifier.internalOwnerName())
            .orElseGet(() -> {
                List<Type> targetTypes = targetTypes();
                if (targetTypes.size() == 1) {
                    return targetTypes.getFirst().getInternalName();
                }
                return null;
            });
        if (owner == null) {
            return null;
        }
        // Find target class
        ClassNode targetClass = lookup.getClass(owner).orElse(null);
        if (targetClass == null) {
            return null;
        }
        // Find target method in class
        String desc = qualifier.desc();
        List<MethodNode> candidates = targetClass.methods.stream()
            .filter(mtd -> mtd.name.equals(qualifier.name()) && (ignoreDesc || desc == null || mtd.desc.equals(desc)))
            .toList();
        // If there's multiple candidates, try removing bouncer methods
        if (candidates.size() > 1 && desc == null) {
            candidates = candidates.stream().filter(mtd -> (mtd.access & Opcodes.ACC_SYNTHETIC) == 0 && (mtd.access & Opcodes.ACC_BRIDGE) == 0).toList();
        }
        return Pair.of(targetClass, candidates);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ClassNode getMixinClass() {
        return this.classNode;
    }

    @Override
    public MethodNode getMixinMethod() {
        return this.methodNode;
    }

    @Override
    public AnnotationHandle rawClassAnnotation() {
        return this.rawClassAnnotation;
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
        private AnnotationHandle rawClassAnnotation;
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

        public Builder rawClassAnnotation(AnnotationHandle annotation) {
            this.rawClassAnnotation = annotation;
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
            return new MethodContextImpl(this.classNode, this.rawClassAnnotation, this.classAnnotation, this.methodNode, this.methodAnnotation, this.injectionPointAnnotation, List.copyOf(this.targetTypes), List.copyOf(this.matchingTargets), context);
        }
    }
}
