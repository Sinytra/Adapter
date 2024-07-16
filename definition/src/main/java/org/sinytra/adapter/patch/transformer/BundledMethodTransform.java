package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.util.MethodTransformBuilderImpl;

import java.util.Collections;
import java.util.List;

public record BundledMethodTransform(List<MethodTransform> transforms, boolean failFast) implements MethodTransform {
    public BundledMethodTransform(List<MethodTransform> transforms) {
        this(transforms, false);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Patch.Result result = Patch.Result.PASS;
        for (MethodTransform transform : this.transforms) {
            Patch.Result transformResult = transform.apply(classNode, methodNode, methodContext, context);
            if (transformResult == Patch.Result.PASS && this.failFast) {
                return result;
            }
            result = result.or(transformResult);
        }
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends MethodTransformBuilderImpl<Builder> {
        private Builder() {

        }

        public BundledMethodTransform build() {
            return build(false);
        }

        public BundledMethodTransform build(boolean failFast) {
            return new BundledMethodTransform(Collections.unmodifiableList(this.transforms), failFast);
        }

        public Patch.Result apply(MethodContext methodContext) {
            return build().apply(methodContext);
        }
    }
}
