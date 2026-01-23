package org.sinytra.adapter.next.pipeline;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.patch.api.TargetPair;

/**
 * Defines the initial and desired states, which include mixin method metadata and per-mixin-type variables.
 *
 * @param clean original state before patching
 * @param dirty desired state necessary to fix the mixin
 */
public record Recipe(Configuration clean, Configuration dirty, Resolvers resolvers, Processors processors, MixinContext context) {
    public Recipe withDirtyConfig(Configuration dirty) {
        return new Recipe(this.clean, dirty, this.resolvers, this.processors, this.context);
    }

    public TargetPair getCleanTarget() {
        if (clean.getTargetMethod() == null) return null; 
        return context.methods().findOwnMethodPair(context.cleanLookup(), clean.getTargetMethod());
    }

    public TargetPair getDirtyTarget() {
        if (dirty.getTargetMethod() == null) return null;
        return context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
    }

    public boolean hasInjectionPointValue(String value) {
        AtData at = clean.getAtData();
        return at != null && value.equals(at.getValue());
    }
}
