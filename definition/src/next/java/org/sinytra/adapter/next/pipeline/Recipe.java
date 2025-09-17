package org.sinytra.adapter.next.pipeline;

import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;

/**
 * Defines the initial and desired states, which include mixin method metadata and per-mixin-type variables.
 * 
 * @param clean original state before patching
 * @param dirty desired state necessary to fix the mixin
 */
public record Recipe(Configuration clean, MutableConfiguration dirty, Resolvers resolvers, Processors processors) {
}
