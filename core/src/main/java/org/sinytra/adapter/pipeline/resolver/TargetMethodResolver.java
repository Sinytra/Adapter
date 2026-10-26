package org.sinytra.adapter.pipeline.resolver;

import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.OriginLocator;

import java.util.List;

public class TargetMethodResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        if (recipe.dirty().getTargetMethod() != null) {
            return ResolutionResult.pass();
        }
        if (recipe.getNewCleanTarget() != null) {
            return ResolutionResult.success(recipe.dirty().copyClean().inheritTargetMethod());
        }

        TargetPair cleanTarget = recipe.getCleanTarget();
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();
        List<OriginLocator.Origin> origins = cleanTarget != null && cleanQualifier != null
            ? OriginLocator.locate(context, cleanTarget, recipe.clean().getTargetClass(), cleanQualifier)
            : List.of();
        OriginLocator.Origin origin = origins.isEmpty() ? null : origins.getFirst();
        boolean hasAt = recipe.clean().getAtData() != null;
        boolean ambiguousPiece = origin != null && origin.piece() && (!hasAt || origins.stream().filter(OriginLocator.Origin::matches).count() > 1);
        if (origin == null || hasAt && !origin.matches() || ambiguousPiece) {
            return hasAt ? ResolutionResult.pass() : ResolutionResult.fail();
        }
        context.recordCtxAudit("Located target method %s (similarity %.2f%s)", MethodQualifier.create(origin.method().methodNode()).asDescriptor(),
            origin.similarity(), origin.matches() ? ", injection point matches" : "");
        return ResolutionResult.success(MutableConfiguration.create().setTargetMethod(origin.method().methodNode()));
    }
}
