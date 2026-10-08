package org.sinytra.adapter.patch.mixin;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.EnumSet;
import java.util.Set;

/**
 * Handles configuration and behavior specific to a Mixin type
 */
public interface MixinType {
    PropertyContainerTemplate getConfigurationTemplate();

    default Set<MixinFlag> getFlags() {
        return EnumSet.noneOf(MixinFlag.class);
    }

    default boolean canInject(MixinContext context, Configuration config) {
        return true;
    }

    /**
     * Derives the target qualifier from the mixin method for types whose target is not named by a
     * {@code method} property, e.g. {@code @Overwrite} and {@code @Invoker}.
     */
    @Nullable
    default MethodQualifier deriveTarget(PropertyContainer properties, MethodNode method) {
        return null;
    }

    /**
     * Whether this handler targets a method and therefore belongs in the method-target pipeline.
     * {@code @Accessor} returns false.
     */
    default boolean targetsMethod() {
        return true;
    }

    /** Validation for types that opted out of the method pipeline; they must record their own audit result. */
    default void validateNonMethodTarget(MixinContext context, Configuration config) {
    }

    TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors);

    TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe);
}
