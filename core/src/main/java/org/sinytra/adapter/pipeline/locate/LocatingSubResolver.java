package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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
        AtData cleanAt = recipe.clean().getAtData();
        List<AbstractInsnNode> cleanInsns = context.injections().find(targets.clean()).stream()
            .map(insn -> cleanAt != null ? Describer.toLocatable(context, cleanAt, insn) : insn)
            .toList();
        return locateAll(context, recipe, targets, cleanInsns, this.locators, candidate -> Describer.describe(context, recipe, candidate));
    }

    @Nullable
    public static Configuration locateAll(MixinContext context, Recipe recipe, Targets targets, List<AbstractInsnNode> cleanInsns,
                                          List<Locator> locators, Function<Candidate, @Nullable Configuration> describer) {
        if (cleanInsns.isEmpty()) {
            return null;
        }
        Configuration shared = null;
        for (AbstractInsnNode cleanInsn : cleanInsns) {
            Configuration config = locate(context, recipe, targets, cleanInsn, locators, describer);
            if (config == null || shared != null && !Describer.isSame(config, shared)) {
                return null;
            }
            shared = config;
        }
        return shared;
    }

    @Nullable
    private static Configuration locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn,
                                        List<Locator> locators, Function<Candidate, @Nullable Configuration> describer) {
        for (Locator locator : locators) {
            List<Candidate> candidates = locator.locate(context, recipe, targets, cleanInsn);
            if (candidates.isEmpty()) {
                continue;
            }
            Candidate candidate = pick(candidates);
            Configuration config = candidate != null ? describer.apply(candidate) : describeAll(candidates, describer);
            if (config != null) {
                context.recordCtxAudit("Located injection point with %s (%s)", locator.getClass().getSimpleName(),
                    candidate != null ? candidate.confidence() : candidates.size() + " candidates with one @At");
            }
            return config;
        }
        return null;
    }

    @Nullable
    private static Configuration describeAll(List<Candidate> candidates, Function<Candidate, @Nullable Configuration> describer) {
        Configuration shared = null;
        for (Candidate candidate : candidates) {
            Configuration config = describer.apply(candidate);
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
