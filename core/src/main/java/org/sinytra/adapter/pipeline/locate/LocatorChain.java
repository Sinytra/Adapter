package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.*;
import java.util.function.Function;

public final class LocatorChain {
    private final List<Locator> locators;
    private final ScopeSearch.Reach reach;

    public LocatorChain(List<Locator> locators, ScopeSearch.Reach reach) {
        this.locators = locators;
        this.reach = reach;
    }

    @Nullable
    public Configuration resolve(MixinContext context, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) {
            return null;
        }
        AtData cleanAt = recipe.clean().getAtData();
        List<AbstractInsnNode> cleanInsns = context.injections().find(cleanTarget).stream()
            .map(insn -> cleanAt != null ? Describer.toLocatable(context, cleanAt, insn) : insn)
            .toList();
        ScopeSearch search = ScopeSearch.of(context, recipe, this.reach);
        return locateAll(context, recipe, search, cleanInsns, this.locators, candidate -> Describer.describe(context, recipe, candidate));
    }

    @Nullable
    public static Configuration locateAll(MixinContext context, Recipe recipe, ScopeSearch search, List<AbstractInsnNode> cleanInsns,
                                          List<Locator> locators, Function<Candidate, @Nullable Configuration> describer) {
        if (cleanInsns.isEmpty()) {
            return null;
        }
        Configuration shared = null;
        for (AbstractInsnNode cleanInsn : cleanInsns) {
            Configuration config = locate(context, recipe, search, cleanInsn, locators, describer);
            if (config == null || shared != null && !Describer.isSame(config, shared)) {
                return null;
            }
            shared = config;
        }
        return shared;
    }

    @Nullable
    private static Configuration locate(MixinContext context, Recipe recipe, ScopeSearch search, AbstractInsnNode cleanInsn, List<Locator> locators,
                                        Function<Candidate, @Nullable Configuration> describer) {
        for (Locator locator : locators) {
            for (int tier = 0; tier < search.tierCount(); tier++) {
                Map<Candidate, Scope> candidates = new HashMap<>();
                List<Candidate> ordered = new ArrayList<>();
                for (Scope scope : search.tier(tier)) {
                    for (Candidate candidate : locator.locate(context, recipe, scope, cleanInsn)) {
                        candidates.put(candidate, scope);
                        ordered.add(candidate);
                    }
                }
                if (ordered.isEmpty()) {
                    continue;
                }
                List<Candidate> best = pick(ordered, candidates);
                Configuration config = best.size() == 1 ? describer.apply(best.getFirst()) : describeAll(best, describer);
                if (config != null) {
                    Candidate first = best.getFirst();
                    context.recordCtxAudit("Located injection point with %s (%s) in %s", locator.getClass().getSimpleName(),
                        best.size() == 1 ? first.confidence() : best.size() + " candidates with one @At",
                        candidates.get(first).kind().name().toLowerCase(Locale.ROOT) + " " + first.target().methodNode().name);
                }
                return config;
            }
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

    private static List<Candidate> pick(List<Candidate> candidates, Map<Candidate, Scope> scopes) {
        MethodAlignment.Confidence best = candidates.stream()
            .map(Candidate::confidence)
            .min(Comparator.naturalOrder())
            .orElseThrow();
        List<Candidate> confident = candidates.stream()
            .filter(c -> c.confidence() == best)
            .toList();
        double similarity = confident.stream().mapToDouble(c -> scopes.get(c).similarity()).max().orElseThrow();
        return confident.stream()
            .filter(c -> scopes.get(c).similarity() == similarity)
            .toList();
    }
}
