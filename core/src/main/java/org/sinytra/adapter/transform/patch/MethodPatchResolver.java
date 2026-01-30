package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.Configurations;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class MethodPatchResolver implements Resolver {
    private final List<MethodPatch> patches;

    public MethodPatchResolver(List<MethodPatch> patches) {
        this.patches = patches;
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

            return ResolutionResult.replace(dirtyConfig);
        }

        return ResolutionResult.pass();
    }
}
