package org.sinytra.adapter.pipeline.processor;

import org.sinytra.adapter.pipeline.processor.relocate.RelocationProcessor;
import org.sinytra.adapter.pipeline.processor.signature.SignatureProcessor;
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
        // MixinExtras expression of an expression @At before anything evaluates the dirty @At
        add(new ExpressionProcessor());
        // Target method post processor
        add(new TargetMethodProcessor());
        // Mixin method signature
        add(new SignatureProcessor());
        // Sugar @Local parameters, once the signature fits the dirty target
        add(new SugarLocalProcessor());
        // Side effects that must only happen once the mixin is otherwise patched
        add(new SplitCancellationProcessor());

        // Late extract processor - must come last
        // Changed class target
        add(new RelocationProcessor());
    }
}
