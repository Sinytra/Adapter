package org.sinytra.adapter.pipeline;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodLocator;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.util.Cached;

import java.util.Objects;

/**
 * Defines the initial and desired states, which include mixin method metadata and per-mixin-type variables.
 */
public final class Recipe {
    private final Configuration clean;
    private final Configuration dirty;
    private final Resolvers resolvers;
    private final Processors processors;
    private final MixinContext context;

    private final Cached<TargetPair> cleanTarget;
    private final Cached<TargetPair> newCleanTarget;
    private final Cached<LocalVariableLookup> cleanLocalsTable;

    private final Cached<TargetPair> dirtyTarget;
    private final Cached<LocalVariableLookup> dirtyLocalsTable;

    /**
     * @param clean original state before patching
     * @param dirty desired state necessary to fix the mixin
     */
    public Recipe(Configuration clean, Configuration dirty, Resolvers resolvers, Processors processors, MixinContext context) {
        this(clean, dirty, resolvers, processors, context, null);
    }

    private Recipe(Configuration clean, Configuration dirty, Resolvers resolvers, Processors processors, MixinContext context, @Nullable Recipe original) {
        this.clean = clean;
        this.dirty = dirty;
        this.resolvers = resolvers;
        this.processors = processors;
        this.context = context;

        if (original != null) {
            this.cleanTarget = original.cleanTarget;
            this.newCleanTarget = original.newCleanTarget;
            this.cleanLocalsTable = original.cleanLocalsTable;
        } else {
            this.cleanTarget = Cached.of(() -> findTarget(context.cleanMethods(), clean.getTargetMethod()));
            this.newCleanTarget = Cached.of(() -> findTarget(context.dirtyMethods(), clean.getTargetMethod()));
            this.cleanLocalsTable = Cached.of(() -> getLocalsTable(getCleanTarget()));
        }

        this.dirtyTarget = Cached.keyed(this::getDirtyTargetQualifier, target -> findTarget(context.dirtyMethods(), target));
        this.dirtyLocalsTable = Cached.keyed(this::getDirtyTarget, target -> getLocalsTable(target));
    }

    @Nullable
    private MethodQualifier getDirtyTargetQualifier() {
        MethodQualifier target = this.dirty.getTargetMethod();
        String targetClass = this.dirty.getTargetClass();
        if (target != null && target.owner() == null && targetClass != null && !targetClass.equals(this.clean.getTargetClass())) {
            return target.withOwner(Type.getObjectType(targetClass));
        }
        return target;
    }

    @Nullable
    private static TargetPair findTarget(MethodLocator locator, @Nullable MethodQualifier target) {
        return target != null ? locator.resolve(target) : null;
    }

    @Nullable
    private static LocalVariableLookup getLocalsTable(@Nullable TargetPair target) {
        return target != null ? LocalVariableLookup.of(target.methodNode()) : null;
    }

    @Nullable
    public LocalVariableLookup cleanLocalsTable() {
        return this.cleanLocalsTable.get();
    }

    @Nullable
    public LocalVariableLookup dirtyLocalsTable() {
        return this.dirtyLocalsTable.get();
    }

    public Recipe withDirtyConfig(Configuration dirty) {
        return new Recipe(this.clean, dirty, this.resolvers, this.processors, this.context, this);
    }

    public TargetPair getCleanTarget() {
        return this.cleanTarget.get();
    }

    public TargetPair getNewCleanTarget() {
        return this.newCleanTarget.get();
    }

    public TargetPair getDirtyTarget() {
        return this.dirtyTarget.get();
    }

    @Nullable
    public Targets targets() {
        TargetPair clean = getCleanTarget();
        TargetPair dirty = getDirtyTarget();
        return clean != null && dirty != null ? new Targets(clean, dirty) : null;
    }

    @Nullable
    public AbstractInsnNode getCleanInjectionInsn() {
        return this.context.injections().resolve(getCleanTarget());
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
