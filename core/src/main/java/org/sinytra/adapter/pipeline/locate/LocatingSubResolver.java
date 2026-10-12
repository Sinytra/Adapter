package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.Comparator;
import java.util.List;

public final class LocatingSubResolver implements SubResolver {
    private final List<Locator> locators;

    public LocatingSubResolver(List<Locator> locators) {
        this.locators = locators;
    }

    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        Targets targets = recipe.targets();
        if (targets == null) {
            return null;
        }

        List<AbstractInsnNode> cleanInsns = context.injections().find(targets.clean());
        if (cleanInsns.isEmpty()) {
            return null;
        }

        Configuration shared = null;
        for (AbstractInsnNode cleanInsn : cleanInsns) {
            Configuration config = locate(context, recipe, targets, cleanInsn);
            if (config == null || shared != null && !Describer.isSame(config, shared)) {
                return null;
            }
            shared = config;
        }
        return shared;
    }

    @Nullable
    private Configuration locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn) {
        for (Locator locator : this.locators) {
            List<Candidate> candidates = locator.locate(context, recipe, targets, cleanInsn);
            if (candidates.isEmpty()) {
                continue;
            }
            Candidate candidate = pick(candidates);
            Configuration config = candidate != null ? Describer.describe(context, recipe, candidate) : describeAll(context, recipe, candidates);
            if (config != null) {
                context.recordCtxAudit("Located injection point with %s (%s)", locator.getClass().getSimpleName(),
                    candidate != null ? candidate.confidence() : candidates.size() + " candidates with one @At");
            }
            // A locator that finds several places can't tell which one is right, so don't guess with later locators either
            return config;
        }
        return null;
    }

    @Nullable
    private static Configuration describeAll(MixinContext context, Recipe recipe, List<Candidate> candidates) {
        Configuration shared = null;
        for (Candidate candidate : candidates) {
            Configuration config = Describer.describe(context, recipe, candidate);
            if (config == null || shared != null && !Describer.isSame(config, shared)) {
                return null;
            }
            shared = config;
        }
        return shared;
    }

    @Nullable
    private static Candidate pick(List<Candidate> candidates) {
        MethodAlignment.Confidence best = candidates.stream()
            .map(Candidate::confidence)
            .min(Comparator.naturalOrder())
            .orElseThrow();
        List<Candidate> confident = candidates.stream()
            .filter(c -> c.confidence() == best)
            .toList();
        return confident.size() == 1 ? confident.getFirst() : null;
    }
}
