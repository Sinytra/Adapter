package org.sinytra.adapter.pipeline.rewrite;

import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolver;

import java.util.List;

public final class RewriteResolver implements Resolver {
    private final List<RewriteRule> rules;

    public RewriteResolver(List<RewriteRule> rules) {
        this.rules = rules;
    }

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        for (RewriteRule rule : this.rules) {
            Rewrite rewrite = context.audited(rule, () -> rule.apply(context, recipe));
            switch (rewrite) {
                case null -> {
                }
                case Rewrite.Replace(var config) -> {
                    context.recordCtxAudit("Rewrite with %s", rule.getClass().getSimpleName());
                    return ResolutionResult.replace(config);
                }
                case Rewrite.Delete(String reason) -> {
                    context.recordCtxAudit("Delete with %s: %s", rule.getClass().getSimpleName(), reason);
                    return ResolutionResult.replace(Configurations.DELETE);
                }
            }
        }
        return ResolutionResult.pass();
    }
}
