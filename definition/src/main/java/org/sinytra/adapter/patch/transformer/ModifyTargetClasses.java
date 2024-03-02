package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record ModifyTargetClasses(Consumer<List<Type>> consumer) implements MethodTransform {

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationValueHandle<?> handle = methodContext.classAnnotation();
        if (handle != null && handle.getKey().equals("value")) {
            AnnotationValueHandle<List<Type>> valueHandle = (AnnotationValueHandle<List<Type>>) handle;

            List<Type> types = new ArrayList<>(valueHandle.get());
            this.consumer.accept(types);
            valueHandle.set(types);
            return Patch.Result.APPLY;
        }
        return Patch.Result.PASS;
    }
}
