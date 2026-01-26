package org.sinytra.adapter.next.transform.patch;

import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.next.env.ctx.PatchResult;

import java.util.ArrayList;
import java.util.List;

public class MethodPatchTransformer implements MethodTransformer {
    private final List<MethodPatch> patches;

    public MethodPatchTransformer(List<MethodPatch> patches) {
        this.patches = patches;
    }

    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        Processors processors = new Processors();

        List<MethodTransformer> postChanges = new ArrayList<>();
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

        // Apply changes
        if (matched) {
            Resolvers resolvers = new Resolvers(false);
            Recipe recipe = new Recipe(config, dirtyConfig, resolvers, processors, context);

            processors.freeze();
            for (Processor processor : processors.getAll()) {
                TxResult res = processor.process(context, dirtyConfig, recipe);
                if (res == TxResult.FINALIZE) {
                    break;
                }
                if (res == TxResult.FAIL) {
                    return PatchResult.PASS;
                }
            }

            // Apply custom changes
            for (MethodTransformer transformer : postChanges) {
                transformer.apply(context, config);
            }
            
            return PatchResult.APPLY;
        }

        return PatchResult.PASS;
    }
}
