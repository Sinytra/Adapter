package org.sinytra.adapter.next.pipeline.processor;

import org.sinytra.adapter.next.env.OrderedRegistry;

public class Processors extends OrderedRegistry<Processor> {

    public Processors() {
        registerDefaultProcessors();
    }

    private void registerDefaultProcessors() {
        add(new DisableMixinProcessor());
        add(new MixinTypeProcessor());
        add(new TargetMethodProcessor());
        add(new InjectionTargetProcessor());
        add(new ParametersProcessor());
        add(new ReturnTypeProcessor());
        add(new PropertyProcessor());
    }
}
