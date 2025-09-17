package org.sinytra.adapter.next.pipeline;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.config.ConfigurationImpl;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.util.MethodQualifier;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PipelineExecutor {
    private final MixinType mixinType;
    private final ClassTarget classTarget;
    private final MixinContext context;

    public PipelineExecutor(MixinType<?> mixinType, ClassTarget classTarget, MixinContext context) {
        this.mixinType = mixinType;
        this.classTarget = classTarget;
        this.context = context;
    }

    public Patch.Result execute() {
        // TODO Preprocessors
        MixinData data = parseMixinData();
        if (data == null) {
            return Patch.Result.PASS;
        }

        Resolvers resolvers = new Resolvers();
        Processors processors = new Processors();

        // 1. Create clean config
        ConfigurationImpl cleanConfig = new ConfigurationImpl(data.getTargetClass(), data.getTargetMethod(), data.at());
        ConfigurationImpl dirtyConfig = new ConfigurationImpl(null, null, null);
        Recipe recipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors);
        this.mixinType.preProcess(data, this.context, cleanConfig, recipe);

        // 2. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            TxResult res = resolver.resolve(data, this.context, dirtyConfig, recipe);
            if (res == TxResult.FAIL) {
                return Patch.Result.PASS;
            }
        }

        // 3. Complete dirty config
        this.mixinType.postProcess(data, this.context, dirtyConfig, recipe);

        // 4. Run Processors
        processors.freeze();
        for (Processor processor : processors.getAll()) {
            TxResult res = processor.process(data, this.context, recipe);
            if (res == TxResult.FAIL) {
                return Patch.Result.PASS;
            }
        }

        return Patch.Result.APPLY;
    }

    @Nullable
    private MixinData parseMixinData() {
        AnnotationHandle atHandle = this.context.legacy().injectionPointAnnotation();
        if (atHandle == null) {
            return null;
        }

        AtData atData = AtData.parse(atHandle).orElse(null);
        if (atData == null) {
            return null;
        }

        MethodQualifier targetMethod = this.context.legacy().getTargetMethodQualifier();
        AnnotationHandle methodHandle = this.context.legacy().methodAnnotation();
        return this.mixinType.parse(this.classTarget, targetMethod, atData, methodHandle);
    }
}
