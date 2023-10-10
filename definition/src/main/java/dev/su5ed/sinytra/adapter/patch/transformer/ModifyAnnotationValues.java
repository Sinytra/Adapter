package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.Predicate;

public record ModifyAnnotationValues(Predicate<AnnotationHandle> annotation) implements MethodTransform {
    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        return this.annotation.test(methodContext.methodAnnotation()) ? Result.APPLY : Result.PASS;
    }
}
