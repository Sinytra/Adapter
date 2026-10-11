package org.sinytra.adapter.pipeline.manual;

import org.sinytra.adapter.api.MethodPatch;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class MethodPatchResolver implements Resolver {
    private final List<MethodPatch> patches;

    public MethodPatchResolver(List<MethodPatch> patches) {
        this.patches = patches;
    }

    public boolean matches(Configuration config) {
        for (MethodPatch patch : this.patches) {
            if (patch.matcher().match(config)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        List<MethodTransformer> postChanges = new ArrayList<>();

        Configuration config = recipe.clean();
        MutableConfiguration dirtyConfig = config.copyClean();
        boolean matched = false;
        for (MethodPatch patch : this.patches) {
            if (patch.matcher().match(config)) {
                matched = true;

                Configuration patchConfig = patch.configuration();
                dirtyConfig.mergeFrom(patchConfig);

                // Add dynamic properties
                BiConsumer<Configuration, MutableConfiguration> completer = patch.configCompleter();
                completer.accept(recipe.clean(), dirtyConfig);

                postChanges.addAll(patch.transforms());
            }
        }

        if (matched) {
            if (!postChanges.isEmpty()) {
                // TODO Might get cancelled by earlier processor
                recipe.processors().add(new MethodPatchProcessor(postChanges));
            }

            // TODO Ugly hardcoding
            if (dirtyConfig.shouldDelete()) {
                return ResolutionResult.replace(Configurations.DELETE);
            }

            return ResolutionResult.success(dirtyConfig);
        }

        return ResolutionResult.pass();
    }
}
