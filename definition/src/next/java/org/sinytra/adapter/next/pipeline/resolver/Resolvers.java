package org.sinytra.adapter.next.pipeline.resolver;

import org.sinytra.adapter.next.env.OrderedRegistry;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.next.pipeline.resolver.special.SliceBoundaryResolver;
import org.sinytra.adapter.next.pipeline.resolver.target.TargetMethodResolver;

public class Resolvers extends OrderedRegistry<Resolver> {

    public Resolvers() {
        this(true);
    }

    public Resolvers(boolean registerDefault) {
        if (registerDefault) {
            registerDefaultResolvers();
        }
    }

    private void registerDefaultResolvers() {
        add(new TargetMethodResolver());
        add(new SliceBoundaryResolver());
        add(new InjectionPointResolver());
    }
}
