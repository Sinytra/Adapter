package org.sinytra.adapter.next.pipeline.resolver;

import org.sinytra.adapter.next.env.OrderedRegistry;
import org.sinytra.adapter.next.pipeline.resolver.target.SplitTargetMethodSubResolver;

public class Resolvers extends OrderedRegistry<Resolver> {

    public Resolvers() {
        registerDefaultResolvers();
    }

    private void registerDefaultResolvers() {
        TargetMethodResolver targetMethodResolver = new TargetMethodResolver();
        targetMethodResolver.addSubResolver(new SplitTargetMethodSubResolver());
        add(targetMethodResolver);

        add(new InjectionTargetResolver());
    }
}
