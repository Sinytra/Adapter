package org.sinytra.adapter.next.pipeline.resolver;

import org.sinytra.adapter.next.env.OrderedRegistry;

public class Resolvers extends OrderedRegistry<Resolver> {

    public Resolvers() {
        registerDefaultResolvers();
    }

    private void registerDefaultResolvers() {
        add(new TargetMethodResolver());
        add(new InjectionTargetResolver());
    }
}
