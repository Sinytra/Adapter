package org.sinytra.adapter.edit.param;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public record TransformParameters(List<ParameterTransformer> transformers, boolean withOffset) {

    public PatchResult apply(MixinContext context) {
        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        if (methodNode.localVariables == null) {
            methodNode.localVariables = new ArrayList<>();
        }

        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        PatchResult result = PatchResult.PASS;

        int offset = calculateOffset(context);
        for (ParameterTransformer transform : transformers) {
            result = result.or(transform.apply(classNode, methodNode, context, newParameterTypes, offset));
        }

        if (result != PatchResult.PASS) {
            updateDescription(context, methodNode, newParameterTypes);
        }

        return result;
    }

    public boolean isEmpty() {
        return this.transformers.isEmpty();
    }

    private void updateDescription(MixinContext context, MethodNode methodNode, List<Type> parameters) {
        Type returnType = Type.getReturnType(methodNode.desc);
        methodNode.desc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        methodNode.signature = null;

        context.recordCtxAudit("Change descriptor to %s", methodNode.desc);
    }

    private int calculateOffset(MixinContext context) {
        // If it's a redirect, the first local variable (index 1) is the object instance
        boolean needsLocalOffset = context.hasFlag(MixinFlag.ACCEPTS_INSTANCE);
        return !context.isStatic() && this.withOffset && needsLocalOffset ? 1 : 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    @CanIgnoreReturnValue
    public static class Builder {
        private final List<ParameterTransformer> transformers = new ArrayList<>();
        private boolean offset = false;

        public Builder transform(ParameterTransformer transformer) {
            this.transformers.add(transformer);
            return this;
        }

        public Builder transform(List<? extends ParameterTransformer> transformers) {
            this.transformers.addAll(transformers);
            return this;
        }

        public Builder remove(int index) {
            return transform(new RemoveParameterTransformer(index));
        }

        public Builder withOffset() {
            this.offset = true;
            return this;
        }

        public Builder withOffset(boolean offset) {
            this.offset = offset;
            return this;
        }

        public Builder chain(Consumer<Builder> consumer) {
            consumer.accept(this);
            return this;
        }

        @CheckReturnValue
        public TransformParameters build() {
            return new TransformParameters(this.transformers, this.offset);
        }
    }
}
