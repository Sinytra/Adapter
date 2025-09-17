package org.sinytra.adapter.next.env.ann;

import com.mojang.datafixers.util.Either;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;

import java.util.List;

public class ClassTarget {
    private final Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either;

    public ClassTarget(Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>> either) {
        this.either = either;
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

    @Nullable
    public static ClassTarget parse(AnnotationHandle handle) {
        return handle.<List<Type>>getValue("value")
            .<Either<AnnotationValueHandle<List<Type>>, AnnotationValueHandle<List<String>>>>map(Either::left)
            .or(() -> handle.<List<String>>getValue("targets").map(Either::right))
            .map(ClassTarget::new)
            .orElse(null);
    }
}
