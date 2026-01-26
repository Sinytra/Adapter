package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.util.OrderedRegistry;
import org.sinytra.adapter.patch.processor.extract.ExtractMixinProcessor;

public class Processors extends OrderedRegistry<Processor> {

    public Processors() {
        registerDefaultProcessors();
    }

    private void registerDefaultProcessors() {
        add(new DisableMixinProcessor());
        add(new ExtractMixinProcessor());
        add(new MixinTypeProcessor());
        add(new TargetMethodProcessor());
        add(new InjectionTargetProcessor());
        add(new ParametersProcessor());
        add(new ReturnTypeProcessor());
        add(new PropertyProcessor());
        add(new StaticAccessProcessor());
    }
}
