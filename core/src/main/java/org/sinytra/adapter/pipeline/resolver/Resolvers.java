package org.sinytra.adapter.pipeline.resolver;

import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.special.SliceBoundaryResolver;
import org.sinytra.adapter.pipeline.resolver.target.TargetMethodResolver;
import org.sinytra.adapter.util.OrderedRegistry;

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
