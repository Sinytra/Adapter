package org.sinytra.adapter.analysis.injection;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.sinytra.adapter.mixin.MockMixinRuntime;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.SliceData;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.*;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.*;

public final class InjectionLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinContext context;
    private final Map<CacheKey, List<AbstractInsnNode>> cache;

    private record CacheKey(TargetPair target, boolean ignoreOrdinal, boolean keepShift, boolean ignoreSlice,
                            @Nullable Object injectionPoint, @Nullable Object methodAnnotation
    ) {
    }

    public InjectionLocator(MixinContext context) {
        this.context = context;
        this.cache = new HashMap<>();
    }

    /// {@return instructions matching mixin's @At selector in the target method}
    public List<AbstractInsnNode> find(@Nullable TargetPair target) {
        return find(target, InjectionQuery.current());
    }

    /// {@return instructions matching the query in the target method}
    public List<AbstractInsnNode> find(@Nullable TargetPair target, InjectionQuery query) {
        if (target == null) {
            return List.of();
        }
        if (query.source() != InjectionQuery.Source.CURRENT) {
            return compute(target, query);
        }
        CacheKey key = new CacheKey(
            target, query.ignoreOrdinal(), query.keepShift(), query.ignoreSlice(),
            annotationKey(this.context.injectionPointAnnotation()), annotationKey(this.context.methodAnnotation())
        );
        List<AbstractInsnNode> insns = this.cache.get(key);
        if (insns == null) {
            insns = compute(target, query);
            this.cache.put(key, insns);
        }
        return insns;
    }

    /// {@return a single instruction the mixin's @At matches in the target method or null if there isn't exactly one}
    @Nullable
    public AbstractInsnNode resolve(@Nullable TargetPair target) {
        return resolve(target, InjectionQuery.current());
    }

    /// {@return a single instruction the query matches in the target method or null if there isn't exactly one}
    @Nullable
    public AbstractInsnNode resolve(@Nullable TargetPair target, InjectionQuery query) {
        List<AbstractInsnNode> insns = find(target, query);
        return insns.size() == 1 ? insns.getFirst() : null;
    }

    /// {@return whether the mixin's @At matches any instructions in the target method}
    public boolean matches(@Nullable TargetPair target) {
        return !find(target).isEmpty();
    }

    /// {@return whether the query matches any instructions in the target method}
    public boolean matches(@Nullable TargetPair target, InjectionQuery query) {
        return !find(target, query).isEmpty();
    }

    /// {@return the instructions of the target method within the slice}
    public InsnList slice(TargetPair target, SliceData slice) {
        return computeSlice(target, slice.toAnnotationNode(), createMixinTarget(target));
    }

    private List<AbstractInsnNode> compute(TargetPair target, InjectionQuery query) {
        AnnotationHandle injectionPoint = this.context.injectionPointAnnotation();
        AnnotationHandle source = switch (query.source()) {
            case CURRENT -> injectionPoint;
            case AT, SLICE_BOUNDARY -> injectionPoint != null ? new AnnotationHandle(Objects.requireNonNull(query.at()).toAnnotationNode()) : null;
            case CONSTANT -> this.context.methodAnnotation().getNested(PROPERTY_CONSTANT).orElse(null);
        };
        if (source == null) {
            return List.of();
        }
        AnnotationHandle node = source.copy();
        if (!query.keepShift()) {
            node.removeValues(AT_SHIFT);
        }
        if (query.ignoreOrdinal()) {
            node.removeValues(PROPERTY_ORDINAL);
        }

        IMixinContext mixinContext = MockMixinRuntime.forClass(this.context.classNode().name, target.classNode().name, this.context.environment());
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            InjectionPoint point = parse(mixinContext, query, node);
            Target mixinTarget = createMixinTarget(target);
            InsnList instructions = query.ignoreSlice() ? target.methodNode().instructions : getSlicedInsns(target, mixinTarget);
            if (MockMixinRuntime.injectionPointNeedsSpecialCare(point)) {
                MockMixinRuntime.findModifyVariableInjectionInsns(point, mixinContext, target.methodNode().instructions, targetInsns, mixinTarget);
            } else {
                point.find(target.methodNode().desc, instructions, targetInsns);
            }
        } catch (Throwable e) {
            LOGGER.error("Error finding injection insns", e);
            return List.of();
        }
        return targetInsns;
    }

    private InjectionPoint parse(IMixinContext mixinContext, InjectionQuery query, AnnotationHandle node) {
        AnnotationHandle methodAnnotation = this.context.methodAnnotation();
        return switch (query.source()) {
            case CURRENT, SLICE_BOUNDARY -> InjectionPoint.parse(mixinContext, this.context.methodNode(), methodAnnotation.unwrap(), node.unwrap());
            case AT -> {
                AnnotationHandle parent = methodAnnotation.copy();
                parent.setOrAppendNonNull(PROPERTY_AT, node.unwrap());
                yield InjectionPoint.parse(mixinContext, this.context.methodNode(), parent.unwrap(), node.unwrap());
            }
            case CONSTANT -> new BeforeConstant(mixinContext, node.unwrap(), Type.getReturnType(this.context.methodNode().desc).getDescriptor());
        };
    }

    private static Target createMixinTarget(TargetPair target) {
        return MockMixinRuntime.createMixinTarget(target);
    }

    private InsnList getSlicedInsns(TargetPair target, Target mixinTarget) {
        return this.context.methodAnnotation().getValue(PROPERTY_SLICE)
            .map(handle -> handle.get() instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) handle.get())
            .map(slice -> computeSlice(target, slice, mixinTarget))
            .orElse(target.methodNode().instructions);
    }

    private InsnList computeSlice(TargetPair target, AnnotationNode slice, Target mixinTarget) {
        IMixinContext mixinContext = MockMixinRuntime.forClass(this.context.classNode().name, target.classNode().name, this.context.environment());
        ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, this.context.methodNode());
        return MethodSlice.parse(sliceContext, slice).getSlice(mixinTarget);
    }

    @Nullable
    private static Object annotationKey(@Nullable AnnotationHandle handle) {
        return handle != null ? annotationValueKey(handle.unwrap()) : null;
    }

    private static Object annotationValueKey(Object value) {
        if (value instanceof AnnotationNode node) {
            List<Object> key = new ArrayList<>();
            key.add(node.desc);
            if (node.values != null) {
                node.values.forEach(v -> key.add(annotationValueKey(v)));
            }
            return key;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(InjectionLocator::annotationValueKey).toList();
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(InjectionLocator::annotationValueKey).toList();
        }
        return value;
    }
}
