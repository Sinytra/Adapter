package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;
import java.util.function.Predicate;

public record ModifyAnnotationValues(Predicate<AnnotationNode> annotation) implements MethodTransform {
    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        return this.annotation.test(annotation) ? Result.APPLY : Result.PASS;
    }
}
