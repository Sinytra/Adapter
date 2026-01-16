package org.sinytra.adapter.patch.transformer.operation.unit;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public record ModifyInjectionTarget(List<String> replacementMethods) implements MethodTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();

        methodContext.recordAudit(this, "Change mixin target to %s", this.replacementMethods);
        if (annotation.matchesDesc(MixinConstants.OVERWRITE)) {
            if (this.replacementMethods.size() > 1) {
                throw new IllegalStateException("Cannot determine replacement @Overwrite method name, multiple specified: " + this.replacementMethods);
            }
            String replacement = this.replacementMethods.getFirst();
            MethodQualifier.create(replacement)
                .map(MethodQualifier::name)
                .ifPresent(str -> methodNode.name = str);
        } else {
            annotation.<List<String>>getValue("method").ifPresentOrElse(
                handle -> handle.set(this.replacementMethods),
                () -> annotation.appendValue("method", this.replacementMethods)
            );
        }

        return Patch.Result.APPLY;
    }
}