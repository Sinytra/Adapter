package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.OrderedRegistry;
import org.sinytra.adapter.next.pipeline.processor.extract.ExtractMixinProcessor;

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
