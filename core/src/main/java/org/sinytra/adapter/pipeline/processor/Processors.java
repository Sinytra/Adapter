package org.sinytra.adapter.pipeline.processor;

import org.sinytra.adapter.pipeline.processor.extract.ExtractMixinProcessor;
import org.sinytra.adapter.util.OrderedRegistry;

public class Processors extends OrderedRegistry<Processor> {

    public Processors() {
        registerDefaultProcessors();
    }

    private void registerDefaultProcessors() {
        // === Major changes first ===
        // Handle deleted mixins
        add(new DisableMixinProcessor());
        // Replaced mixin types
        add(new MixinTypeProcessor());

        // === Standard changes ===
        // General annotation props
        add(new PropertyProcessor());
        // Target method post processor
        add(new TargetMethodProcessor());
        // Mixin method parameters
        add(new ParametersProcessor());
        // Parameter casts
        add(new ParametersPostProcessor());
        // Mixin method return type
        add(new ReturnTypeProcessor());
        // Static access modifier
        add(new StaticAccessProcessor());
        // Side effects that must only happen once the mixin is otherwise patched
        add(new SplitCancellationProcessor());

        // Late extract processor - must come last
        // Changed class target
        add(new ExtractMixinProcessor());
    }
}
