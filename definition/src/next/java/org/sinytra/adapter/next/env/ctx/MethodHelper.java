package org.sinytra.adapter.next.env.ctx;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.ParamDiffResolver;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodContext.TargetPair;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_SHIFT;
import static org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;

public class MethodHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinContext context;
    private final MethodFinder methodFinder;

    private final Map<TargetPair, List<AbstractInsnNode>> targetInstructionsCache = new HashMap<>();

    public MethodHelper(MixinContext context, List<Type> targetTypes) {
        this.context = context;

        String singleTargetClass = targetTypes.size() == 1 ? targetTypes.getFirst().getInternalName() : null;
        this.methodFinder = new MethodFinder(singleTargetClass);
    }

    @Nullable
    public MethodNode findMethod(ClassLookup lookup, MethodQualifier qualifier) {
        return Optional.ofNullable(findMethodPair(lookup, qualifier))
            .map(TargetPair::methodNode)
            .orElse(null);
    }

    @Nullable
    public MethodNode findOwnMethod(ClassLookup lookup, MethodQualifier qualifier) {
        return Optional.ofNullable(findOwnMethodPair(lookup, qualifier))
            .map(TargetPair::methodNode)
            .orElse(null);
    }

    @Nullable
    public TargetPair findMethodPair(ClassLookup lookup, MethodQualifier qualifier) {
        return this.methodFinder.findMethod(lookup, qualifier, 0);
    }

    @Nullable
    public TargetPair findOwnMethodPair(ClassLookup lookup, MethodQualifier qualifier) {
        return this.methodFinder.findMethod(lookup, qualifier, MethodFinder.Flags.FALLBACK_OWNER);
    }

    @Nullable
    public Pair<ClassNode, List<MethodNode>> findOwnMethodsByName(ClassLookup lookup, MethodQualifier qualifier) {
        return this.methodFinder.findMethods(lookup, qualifier, MethodFinder.Flags.FALLBACK_OWNER | MethodFinder.Flags.IGNORE_DESC);
    }

    public boolean hasInjectionTargetInsns(@Nullable MethodContext.TargetPair target) {
        return !findInjectionTargetInsns(target).isEmpty();
    }

    @Nullable
    public AbstractInsnNode findInjectionTargetInsn(@Nullable MethodContext.TargetPair target) {
        List<AbstractInsnNode> cleanInsns = findInjectionTargetInsns(target);
        return cleanInsns.size() != 1 ? null : cleanInsns.getFirst();
    }

    public List<AbstractInsnNode> findInjectionTargetInsns(@Nullable MethodContext.TargetPair target) {
        return this.targetInstructionsCache.computeIfAbsent(target, this::computeInjectionTargetInsns);
    }

    private List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable MethodContext.TargetPair target) {
        return computeInjectionTargetInsns(
            target,
            this.context::injectionPointAnnotation,
            (ctx, h) -> InjectionPoint.parse(ctx, this.context.methodNode(), this.context.methodAnnotation().unwrap(), h.unwrap()),
            true
        );
    }

    public List<Type> resolveCapturedMethodParams(Configuration clean, Configuration dirty) {
        List<Type> cleanCaptured = clean.getParameters().get(CAPTURED_PARAMS);
        List<Type> dirtyCaptured = new ArrayList<>();

        // Evaluate parameter difference, capture additional params when necessary
        if (!cleanCaptured.isEmpty()) {
            // TODO Clean up boilerplate
            MethodNode cleanTarget = findOwnMethod(this.context.cleanLookup(), clean.getTargetMethod());
            MethodNode dirtyTarget = findOwnMethod(this.context.dirtyLookup(), dirty.getTargetMethod());
            if (cleanTarget == null || dirtyTarget == null) return dirtyCaptured;

            LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.compareMethodParameters(cleanTarget, dirtyTarget);
            List<Type> cleanTargetParams = MethodParameters.getParameterTypes(cleanTarget.desc);
            // Use a sublist instead of cleanCaptured to be able to compare Type instances directly
            List<Type> capturedSublist = cleanTargetParams.subList(0, cleanCaptured.size());
            // Expect cleanTargetParams to begin with or be equal to cleanCaptured 
            if (cleanCaptured.size() > cleanTargetParams.size() || !cleanCaptured.equals(capturedSublist)) {
                return dirtyCaptured;
            }

            ParamDiffResolver.ParamEvalResult evalResult = ParamDiffResolver.resolve(cleanTargetParams, diff);

            int maxIndex = capturedSublist.stream()
                .map(evalResult::getUpdated)
                .filter(Objects::nonNull)
                .mapToInt(ParamDiffResolver.ParamState::dirtyIndex)
                .max()
                .orElse(-1);

            if (maxIndex != -1) {
                List<Type> dirtyTargetParams = MethodParameters.getParameterTypes(dirtyTarget.desc);
                dirtyCaptured = List.copyOf(dirtyTargetParams.subList(0, maxIndex + 1));
            }
        }

        return dirtyCaptured;
    }

    @Nullable
    private List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable MethodContext.TargetPair target, Supplier<AnnotationHandle> atNodeSupplier, BiFunction<IMixinContext, AnnotationHandle, InjectionPoint> injectionPointParser, boolean ignoreShift) {
        if (target == null) {
            return List.of();
        }
        AnnotationHandle atNode = atNodeSupplier.get();
        if (atNode == null) {
            return List.of();
        }
        AnnotationHandle atNodeCopy = atNode.copy();
        if (ignoreShift) {
            atNodeCopy.removeValues(AT_SHIFT);
        }
        PatchContext patchContext = this.context.patchContext();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(this.context.classNode().name, target.classNode().name, patchContext.environment());
        // Parse injection point
        InjectionPoint injectionPoint = injectionPointParser.apply(mixinContext, atNodeCopy);
        Target mixinTarget = MockMixinRuntime.createMixinTarget(target);
        // Find target instructions
        InsnList instructions = getSlicedInsns(this.context.methodAnnotation(), this.context.classNode(), this.context.methodNode(), target.classNode(), target.methodNode(), patchContext, mixinTarget);
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

    public static boolean isStatic(MethodNode node) {
        return (node.access & Opcodes.ACC_STATIC) != 0;
    }
}
