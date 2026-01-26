package org.sinytra.adapter.next.transform.param;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.next.env.ctx.PatchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public record TransformParameters(List<ParameterTransformer> transformers, boolean withOffset) {

    // TODO
    public PatchResult apply(MixinContext context) {
        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();

        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        PatchResult result = PatchResult.PASS;

        int offset = calculateOffset(context);
        for (ParameterTransformer transform : transformers) {
            result = result.or(transform.apply(classNode, methodNode, context, newParameterTypes, offset));
        }

        if (result != PatchResult.PASS) {
            updateDescription(methodNode, newParameterTypes);
        }

        return result;
    }

    private void updateDescription(MethodNode methodNode, List<Type> parameters) {
        Type returnType = Type.getReturnType(methodNode.desc);
        //  recordAudit(transform, "Change descriptor to %s", newDesc);
        methodNode.desc = Type.getMethodDescriptor(returnType, parameters.toArray(Type[]::new));
        methodNode.signature = null;
    }

    private int calculateOffset(MixinContext context) {
        AnnotationHandle annotation = context.methodAnnotation();
        // If it's a redirect, the first local variable (index 1) is the object instance
        boolean needsLocalOffset = annotation.matchesDesc(MixinAnnotations.REDIRECT) || annotation.matchesDesc(MixinAnnotations.WRAP_OPERATION);
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

        public Builder transform(List<ParameterTransformer> transformers) {
            this.transformers.addAll(transformers);
            return this;
        }

        public Builder inject(int parameterIndex, Type type) {
            return this.transform(new InjectParameterTransform(parameterIndex, type));
        }

        public Builder replace(int index, Type type) {
            return transform(new ReplaceParametersTransformer(index, type));
        }

        public Builder replacements(List<Pair<Integer, Type>> replacements) {
            replacements.forEach(p -> replace(p.getFirst(), p.getSecond()));
            return this;
        }

        public Builder swap(int from, int to) {
            return transform(new SwapParametersTransformer(from, to));
        }

        public Builder swaps(List<Pair<Integer, Integer>> swaps) {
            swaps.forEach(p -> swap(p.getFirst(), p.getSecond()));
            return this;
        }

        public Builder substitute(int target, int substitute) {
            return transform(new SubstituteParameterTransformer(target, substitute));
        }

        public Builder substitutes(List<Pair<Integer, Integer>> substitutes) {
            substitutes.forEach(p -> swap(p.getFirst(), p.getSecond()));
            return this;
        }

        public Builder inline(int target, Consumer<InstructionAdapter> adapter) {
            return transform(new InlineParameterTransformer(target, adapter));
        }

        public Builder remove(int index) {
            return transform(new RemoveParameterTransformer(index));
        }

        public Builder removals(List<Integer> removals) {
            removals.forEach(this::remove);
            return this;
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
