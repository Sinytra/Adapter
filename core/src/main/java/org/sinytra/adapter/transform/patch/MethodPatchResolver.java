package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.ArrayList;
import java.util.List;

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
                postChanges.addAll(patch.transforms());
            }
        }

        if (matched) {
            if (!postChanges.isEmpty()) {
                // TODO Might get cancelled by earlier processor
                recipe.processors().add(new MethodPatchProcessor(postChanges));
            }

            return ResolutionResult.replace(dirtyConfig);
        }

        return ResolutionResult.pass();
    }
}
