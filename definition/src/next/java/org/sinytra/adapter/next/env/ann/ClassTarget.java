package org.sinytra.adapter.next.env.ann;

import com.mojang.datafixers.util.Either;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;

import java.util.List;

import static org.sinytra.adapter.next.env.util.MixinAnnotationConstants.MIXIN_TARGETS;
import static org.sinytra.adapter.next.env.util.MixinAnnotationConstants.MIXIN_VALUE;

public class ClassTarget {
    private final AnnotationHandle handle;
    private final Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either;

    public ClassTarget(AnnotationHandle handle, Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either) {
        this.handle = handle;
        this.either = either;
    }

    @Deprecated
    public AnnotationHandle getHandle() {
        return this.handle;
    }

    @Deprecated
    public AnnotationValueHandle<?> getValueHandle() {
        if (this.either.left().isPresent()) {
            return this.either.left().orElseThrow();
        }
        return this.either.right().orElseThrow();
    }

    public List<Type> getTypes() {
        return this.either.map(AnnotationValueHandle::get, h -> h.get()
            .stream()
            .map(Type::getObjectType)
            .toList()
        );
    }

    public Type getSingle() {
        List<Type> types = this.either.map(AnnotationValueHandle::get, h -> h.get()
            .stream()
            .map(Type::getObjectType)
            .toList()
        );
        if (types.size() != 1) {
            throw new IllegalStateException("Expected exactly one type, got " + types.size());
        }
        return types.getFirst();
    }

    @Deprecated
    public void set(Type type) {
        this.either.ifLeft(h -> h.set(List.of(type)))
            .ifRight(h -> h.set(List.of(type.getInternalName())));
    }

    @Nullable
    public static ClassTarget parse(AnnotationHandle handle) {
        return handle.<List<Type>>getValue(MIXIN_VALUE)
            .<Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>>>map(Either::left)
            .or(() -> handle.<List<String>>getValue(MIXIN_TARGETS).map(Either::right))
            .map(v -> new ClassTarget(handle, v))
            .orElse(null);
    }
}
