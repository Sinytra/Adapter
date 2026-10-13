package org.sinytra.adapter.pipeline.resolver;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.ann.SliceData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocatorChain;
import org.sinytra.adapter.pipeline.locate.Locator;
import org.sinytra.adapter.pipeline.locate.ReplacedCallLocator;
import org.sinytra.adapter.pipeline.locate.SameMethodLocator;
import org.sinytra.adapter.pipeline.locate.Scope;
import org.sinytra.adapter.pipeline.locate.ScopeSearch;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.model.config.key.MixinKeys.SLICE;
import static org.sinytra.adapter.model.config.key.MixinKeys.SLICES;

public class SliceBoundaryResolver implements Resolver {
    private static final List<Locator> LOCATORS = List.of(new SameMethodLocator(), new ReplacedCallLocator());

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        Targets targets = recipe.targets();
        if (targets == null) return ResolutionResult.pass();

        SliceData slice = recipe.clean().getProperty(SLICE).orElse(null);
        if (slice != null) {
            SliceData fixed = fixSlice(context, recipe, targets, slice);
            return fixed != null && fixed != slice ? ResolutionResult.success(MutableConfiguration.create().setProperty(SLICE, fixed)) : ResolutionResult.pass();
        }

        List<SliceData> slices = recipe.clean().getProperty(SLICES).orElse(null);
        if (slices == null || slices.isEmpty()) return ResolutionResult.pass();

        List<SliceData> fixedSlices = new ArrayList<>();
        boolean changed = false;
        for (SliceData s : slices) {
            SliceData fixed = fixSlice(context, recipe, targets, s);
            if (fixed == null) return ResolutionResult.pass();
            fixedSlices.add(fixed);
            changed |= fixed != s;
        }
        return changed ? ResolutionResult.success(MutableConfiguration.create().setProperty(SLICES, fixedSlices)) : ResolutionResult.pass();
    }

    @Nullable
    private static SliceData fixSlice(MixinContext context, Recipe recipe, Targets targets, SliceData slice) {
        AtData from = fixBoundary(context, recipe, targets, slice.from());
        AtData to = fixBoundary(context, recipe, targets, slice.to());
        if (slice.from() != null && from == null || slice.to() != null && to == null) {
            return null;
        }
        return from == slice.from() && to == slice.to() ? slice : new SliceData(slice.id(), from, to);
    }

    @Nullable
    private static AtData fixBoundary(MixinContext context, Recipe recipe, Targets targets, @Nullable AtData boundary) {
        if (boundary == null || context.injections().matches(targets.dirty(), InjectionQuery.sliceBoundary(boundary))) {
            return boundary;
        }

        List<AbstractInsnNode> cleanInsns = context.injections().find(targets.clean(), InjectionQuery.of(boundary).ignoringSlice());
        Configuration located = LocatorChain.locateAll(context, recipe, ScopeSearch.of(context, Scope.of(targets)), cleanInsns, LOCATORS, candidate -> {
            if (candidate.target() != targets.dirty()) {
                return null;
            }
            AtData at = Describer.describeAt(context, boundary, candidate, a -> InjectionQuery.of(a).ignoringSlice());
            return at != null ? MutableConfiguration.create().setAtData(at) : null;
        });
        return located != null ? located.getAtData() : null;
    }
}
