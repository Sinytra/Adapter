package org.sinytra.adapter.next.pipeline;

import com.google.common.base.Suppliers;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.env.ctx.TargetPair;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

// TODO Figure out Recipe and Context or merge them
// TODO Look for dirty target using clean config?

/**
 * Defines the initial and desired states, which include mixin method metadata and per-mixin-type variables.
 */
public final class Recipe {
    private final Configuration clean;
    private final Configuration dirty;
    private final Resolvers resolvers;
    private final Processors processors;
    private final MixinContext context;

    private final Supplier<LocalVariableLookup> cleanLocalsTableCache;
    private final Supplier<LocalVariableLookup> dirtyLocalsTableCache;

    /**
     * @param clean original state before patching
     * @param dirty desired state necessary to fix the mixin
     */
    public Recipe(Configuration clean, Configuration dirty, Resolvers resolvers, Processors processors, MixinContext context) {
        this.clean = clean;
        this.dirty = dirty;
        this.resolvers = resolvers;
        this.processors = processors;
        this.context = context;

        this.cleanLocalsTableCache = Suppliers.memoize(() -> Optional.ofNullable(getCleanTarget())
            .map(pair -> new LocalVariableLookup(pair.methodNode()))
            .orElse(null));
        this.dirtyLocalsTableCache = Suppliers.memoize(() -> Optional.ofNullable(getDirtyTarget())
            .map(pair -> new LocalVariableLookup(pair.methodNode()))
            .orElse(null));
    }

    public LocalVariableLookup cleanLocalsTable() {
        return this.cleanLocalsTableCache.get();
    }

    public LocalVariableLookup dirtyLocalsTable() {
        return this.dirtyLocalsTableCache.get();
    }

    public Recipe withDirtyConfig(Configuration dirty) {
        return new Recipe(this.clean, dirty, this.resolvers, this.processors, this.context);
    }

    // TODO Cache
    public TargetPair getCleanTarget() {
        if (clean.getTargetMethod() == null) return null;
        return context.methods().findOwnMethodPair(context.cleanLookup(), clean.getTargetMethod());
    }

    public TargetPair getNewCleanTarget() {
        if (clean.getTargetMethod() == null) return null;
        return context.methods().findOwnMethodPair(context.dirtyLookup(), clean.getTargetMethod());
    }

    public TargetPair getDirtyTarget() {
        if (dirty.getTargetMethod() == null) return null;
        return context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
    }

    public boolean hasInjectionPointValue(String value) {
        AtData at = clean.getAtData();
        return at != null && value.equals(at.getValue());
    }

    public Configuration clean() {
        return clean;
    }

    public Configuration dirty() {
        return dirty;
    }

    public Resolvers resolvers() {
        return resolvers;
    }

    public Processors processors() {
        return processors;
    }

    public MixinContext context() {
        return context;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Recipe) obj;
        return Objects.equals(this.clean, that.clean) &&
            Objects.equals(this.dirty, that.dirty) &&
            Objects.equals(this.resolvers, that.resolvers) &&
            Objects.equals(this.processors, that.processors) &&
            Objects.equals(this.context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clean, dirty, resolvers, processors, context);
    }

    @Override
    public String toString() {
        return "Recipe[" +
            "clean=" + clean + ", " +
            "dirty=" + dirty + ", " +
            "resolvers=" + resolvers + ", " +
            "processors=" + processors + ", " +
            "context=" + context + ']';
    }

}
