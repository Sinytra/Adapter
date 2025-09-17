package org.sinytra.adapter.next.env.ctx;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MethodContext;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class MethodHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinContext mixinContext;
    private final List<Type> targetTypes;

    public MethodHelper(MixinContext mixinContext, List<Type> targetTypes) {
        this.mixinContext = mixinContext;
        this.targetTypes = targetTypes;
    }

    public List<AbstractInsnNode> findInjectionTargetInsns(@Nullable MethodContext.TargetPair target) {
        return computeInjectionTargetInsns(target); // TODO CACHE
    }

    private List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable MethodContext.TargetPair target) {
        return computeInjectionTargetInsns(target, this.mixinContext::injectionPointAnnotation,
            (ctx, h) -> InjectionPoint.parse(ctx, this.mixinContext.methodNode(), this.mixinContext.methodAnnotation().unwrap(), h.unwrap()));
    }

    @Nullable
    public MethodContext.TargetPair findMethod(ClassLookup lookup, MethodQualifier qualifier) {
        Pair<ClassNode, List<MethodNode>> pair = findMethods(lookup, qualifier, true, false);
        if (pair == null) {
            return null;
        }

        if (pair.getSecond().isEmpty()) {
            LOGGER.debug("Target method not found: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        } else if (pair.getSecond().size() > 1) {
            LOGGER.debug("Multiple candidates found for method: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        }
        return new MethodContext.TargetPair(pair.getFirst(), pair.getSecond().getFirst());
    }

    @Nullable
    public Pair<ClassNode, List<MethodNode>> findMethodsIgnoringDesc(ClassLookup lookup, MethodQualifier qualifier) {
        return findMethods(lookup, qualifier, true, true);
    }

    @Nullable
    private Pair<ClassNode, List<MethodNode>> findMethods(ClassLookup lookup, MethodQualifier qualifier, boolean ignoreOwner, boolean ignoreDesc) {
        if (qualifier == null || qualifier.name() == null) {
            return null;
        }

        // Determine target class
        String owner;
        if (!ignoreOwner && qualifier.internalOwnerName() != null) {
            owner = qualifier.internalOwnerName();
        } else if (this.targetTypes.size() == 1) {
            owner = this.targetTypes.getFirst().getInternalName();
        } else {
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

    @Nullable
    private List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable MethodContext.TargetPair target, Supplier<AnnotationHandle> atNodeSupplier, BiFunction<IMixinContext, AnnotationHandle, InjectionPoint> injectionPointParser) {
        if (target == null) {
            return List.of();
        }
        AnnotationHandle atNode = atNodeSupplier.get();
        if (atNode == null) {
            return List.of();
        }
        PatchContext patchContext = this.mixinContext.patchContext();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(this.mixinContext.classNode().name, target.classNode().name, patchContext.environment());
        // Parse injection point
        InjectionPoint injectionPoint = injectionPointParser.apply(mixinContext, atNode);
        Target mixinTarget = MockMixinRuntime.createMixinTarget(target);
        // Find target instructions
        InsnList instructions = getSlicedInsns(this.mixinContext.methodAnnotation(), this.mixinContext.classNode(), this.mixinContext.methodNode(), target.classNode(), target.methodNode(), patchContext, mixinTarget);
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
}
