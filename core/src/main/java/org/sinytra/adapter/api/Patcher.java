package org.sinytra.adapter.api;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.pipeline.PatcherImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches mixin classes so that they apply to the dirty environment.
 */
public interface Patcher {
    /**
     * Patch a mixin class in place. Classes that are not mixins are left unchanged.
     */
    PatchResult process(ClassNode classNode);

    static Builder builder(PatchEnvironment environment) {
        return new Builder(environment);
    }

    class Builder {
        private final PatchEnvironment environment;
        private final List<ClassTransformer> classTransformers = new ArrayList<>();
        private final Multimap<TxPhase, MethodTransformer> methodTransformers = HashMultimap.create();

        public Builder(PatchEnvironment environment) {
            this.environment = environment;
        }

        public Builder classTransformer(ClassTransformer transformer) {
            this.classTransformers.add(transformer);
            return this;
        }

        public Builder classTransformers(List<ClassTransformer> transformers) {
            this.classTransformers.addAll(transformers);
            return this;
        }

        public Builder methodTransformer(TxPhase phase, MethodTransformer transformer) {
            this.methodTransformers.put(phase, transformer);
            return this;
        }

        public Builder methodTransformers(Multimap<TxPhase, MethodTransformer> transformers) {
            this.methodTransformers.putAll(transformers);
            return this;
        }

        public Patcher build() {
            return new PatcherImpl(this.environment, this.classTransformers, this.methodTransformers);
        }
    }
}
