package org.sinytra.adapter.model.ann;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.MIXIN_TARGETS;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.MIXIN_VALUE;

public class ClassTarget {
    private List<Type> types;
    private final Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either;

    public ClassTarget(List<Type> types, Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either) {
        this.types = ImmutableList.copyOf(types);
        this.either = either;
    }

    public List<Type> getTypes() {
        return this.types;
    }

    public void set(Type type) {
        this.types = List.of(type);
        this.either.ifLeft(h -> h.set(List.of(type)))
            .ifRight(h -> h.set(List.of(type.getInternalName())));
    }

    @Nullable
    public static ClassTarget parse(AnnotationHandle handle, RefMapper mapper) {
        return handle.<List<Type>>getValue(MIXIN_VALUE)
            .map(v -> new ClassTarget(v.get(), Either.left(v)))
            .or(() -> handle.<List<String>>getValue(MIXIN_TARGETS).map(v -> {
                List<Type> types = v.get().stream()
                    .map(mapper::remap)
                    .map(name -> Type.getObjectType(name.replace('.', '/')))
                    .toList();
                return new ClassTarget(types, Either.right(v));
            }))
            .orElse(null);
    }
}
