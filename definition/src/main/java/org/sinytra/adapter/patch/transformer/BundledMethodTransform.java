package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;

import java.util.List;

public record BundledMethodTransform(List<MethodTransform> transforms) implements MethodTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Patch.Result result = Patch.Result.PASS;
        for (MethodTransform transform : this.transforms) {
            result = result.or(transform.apply(classNode, methodNode, methodContext, context));
        }
        return result;
    }
}
