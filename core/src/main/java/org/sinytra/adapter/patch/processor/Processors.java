package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.util.OrderedRegistry;
import org.sinytra.adapter.patch.processor.extract.ExtractMixinProcessor;

public class Processors extends OrderedRegistry<Processor> {

    public Processors() {
        registerDefaultProcessors();
    }

    private void registerDefaultProcessors() {
        // === Major changes first ===
        // Handle deleted mixins
        add(new DisableMixinProcessor());
        // Changed class target
        add(new ExtractMixinProcessor());
        // Replaced mixin types
        add(new MixinTypeProcessor());

        // === Standard changes ===
        // General annotation props
        add(new PropertyProcessor());
        // Target method post processor
        add(new TargetMethodProcessor());
        // Mixin method parameters
        add(new ParametersProcessor());
        // Mixin method return type
        add(new ReturnTypeProcessor());
        // Static access modifier
        add(new StaticAccessProcessor());
    }
}
