package org.sinytra.adapter.analysis.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.sinytra.adapter.mixin.MixinBridge;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.SliceData;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.*;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.*;

public final class InjectionLocator {
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
        return session(target).slice(slice.toAnnotationNode());
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

        MixinBridge.Session session = session(target);
        AnnotationNode slice = query.ignoreSlice() ? null : getSlice();
        AnnotationHandle methodAnnotation = this.context.methodAnnotation();
        return switch (query.source()) {
            case CURRENT, SLICE_BOUNDARY -> session.findInjectionPoint(methodAnnotation.unwrap(), node.unwrap(), slice);
            case AT -> {
                AnnotationHandle parent = methodAnnotation.copy();
                parent.setOrAppendNonNull(PROPERTY_AT, node.unwrap());
                yield session.findInjectionPoint(parent.unwrap(), node.unwrap(), slice);
            }
            case CONSTANT -> session.findConstant(node.unwrap(), Type.getReturnType(this.context.methodNode().desc).getDescriptor(), slice);
        };
    }

    private MixinBridge.Session session(TargetPair target) {
        return MixinBridge.session(this.context.classNode().name, this.context.methodNode(), target.classNode(), target.methodNode(), this.context.environment().refmapHolder());
    }

    @Nullable
    private AnnotationNode getSlice() {
        return this.context.methodAnnotation().getValue(PROPERTY_SLICE)
            .map(handle -> handle.get() instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) handle.get())
            .orElse(null);
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
