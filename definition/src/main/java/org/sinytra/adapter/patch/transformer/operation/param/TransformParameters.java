package org.sinytra.adapter.patch.transformer.operation.param;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.*;

import java.util.*;
import java.util.function.Consumer;

public record TransformParameters(List<ParameterTransformer> transformers, boolean withOffset, ParamTransformTarget targetType) implements MethodTransform {

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return this.targetType.getTargetMixinTypes();
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        Patch.Result result = Patch.Result.PASS;

        int offset = calculateOffset(methodContext);
        for (ParameterTransformer transform : transformers) {
            result = result.or(transform.apply(classNode, methodNode, methodContext, context, newParameterTypes, offset));
        }

        if (result != Patch.Result.PASS) {
            methodContext.updateDescription(this, newParameterTypes);
        }

        return result;
    }

    private int calculateOffset(MethodContext methodContext) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        if (this.targetType == ParamTransformTarget.METHOD_EXT && annotation.matchesDesc(MixinConstants.REDIRECT)) {
            MethodNode targetMethod = Optional.ofNullable(methodContext.getInjectionPointMethodQualifier())
                .flatMap(q -> methodContext.patchContext().environment().dirtyClassLookup().findMethod(q.internalOwnerName(), q.name(), q.desc()))
                .orElse(null);
            if (targetMethod != null) {
                return ((targetMethod.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0) + Type.getArgumentTypes(targetMethod.desc).length;
            }
        }
        // If it's a redirect, the first local variable (index 1) is the object instance
        boolean needsLocalOffset = annotation.matchesDesc(MixinConstants.REDIRECT) || annotation.matchesDesc(MixinConstants.WRAP_OPERATION);
        return !methodContext.isStatic() && this.withOffset && needsLocalOffset ? 1 : 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    @CanIgnoreReturnValue
    public static class Builder {
        private final List<ParameterTransformer> transformers = new ArrayList<>();
        private boolean offset = false;
        private ParamTransformTarget targetType = ParamTransformTarget.ALL;

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

        public Builder targetType(ParamTransformTarget targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder chain(Consumer<Builder> consumer) {
            consumer.accept(this);
            return this;
        }

        @CheckReturnValue
        public TransformParameters build() {
            return new TransformParameters(this.transformers, this.offset, this.targetType);
        }
    }
}
