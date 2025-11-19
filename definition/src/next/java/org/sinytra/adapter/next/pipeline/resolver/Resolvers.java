package org.sinytra.adapter.next.pipeline.resolver;

import org.sinytra.adapter.next.env.OrderedRegistry;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.target.TargetMethodResolver;

public class Resolvers extends OrderedRegistry<Resolver> {

    public Resolvers() {
        registerDefaultResolvers();
    }

    private void registerDefaultResolvers() {
        add(new TargetMethodResolver());
        add(new InjectionPointResolver());
    }
}
