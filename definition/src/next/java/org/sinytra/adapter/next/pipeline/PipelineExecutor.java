package org.sinytra.adapter.next.pipeline;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.config.ConfigurationImpl;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.next.pipeline.resolver.Resolver.ResolutionResult;
import org.sinytra.adapter.next.pipeline.resolver.Resolver.ResultType;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.Objects;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PipelineExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinType mixinType;
    private final ClassTarget classTarget;
    private final MixinContext context;

    public PipelineExecutor(MixinType<?> mixinType, ClassTarget classTarget, MixinContext context) {
        this.mixinType = mixinType;
        this.classTarget = classTarget;
        this.context = context;
    }

    public Patch.Result execute() {
        MixinData data = parseMixinData();
        if (data == null) {
            return Patch.Result.PASS;
        }
        String mixinId = this.context.classNode().name + "#" + this.context.methodNode().name + this.context.methodNode().desc;

        Resolvers resolvers = new Resolvers();
        Processors processors = new Processors();
        PropertyContainerTemplate template = Objects.requireNonNull(this.mixinType.getConfigurationTemplate());

        // 1. Create clean config
        ConfigurationImpl cleanConfig = new ConfigurationImpl(template);
        cleanConfig.setMixinType(this.context.methodAnnotation().getDesc());
        cleanConfig.setTargetClass(data.getTargetClass());
        cleanConfig.setTargetMethod(data.getTargetMethod());
        cleanConfig.setAtData(data.at());
        cleanConfig.setReturnType(Type.getReturnType(context.methodNode().desc));

        // 1.1. Create dirty config
        ConfigurationImpl dirtyConfig = new ConfigurationImpl(template, cleanConfig);
        dirtyConfig.inheritMixinType();
        dirtyConfig.inheritTargetClass();

        Recipe recipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors);

        // 2. Complete clean config
        this.mixinType.preProcess(data, this.context, cleanConfig, recipe);

        // 2.1. Validate clean config
        if (!cleanConfig.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            return Patch.Result.PASS;
        }

        // 3. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            ResolutionResult res = resolver.resolve(data, this.context, cleanConfig, dirtyConfig, recipe);
            Objects.requireNonNull(res, "BUG: Received null from resolver " + resolver.getClass());

            if (res.type() == ResultType.SUCCESS || res.type() == ResultType.FINALIZE) {
                dirtyConfig.mergeFrom(res.patch());
                if (res.type() == ResultType.FINALIZE) {
                    break;
                }
            } else if (res.type() == ResultType.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed RESOLVER {}", mixinId, resolver.getClass().getSimpleName());
                return Patch.Result.PASS;
            }
        }

        // TODO Check and make sure resolvers don't modify the method/class (maybe with a propert switch to skip these in prod)

        // 4. Complete dirty config
        this.mixinType.postProcess(data, this.context, cleanConfig, dirtyConfig, recipe);

        // 4.1. Validate dirty config
        if (!dirtyConfig.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid DIRTY config", mixinId);
            return Patch.Result.PASS;
        }

        // 5. Run Processors
        processors.freeze();
        for (Processor processor : processors.getAll()) {
            TxResult res = processor.process(data, this.context, dirtyConfig, recipe);
            if (res == TxResult.FINALIZE) {
                break;
            }
            if (res == TxResult.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed PROCESSOR {}", mixinId, processor.getClass().getSimpleName());
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

        AtData atData = AtData.parse(atHandle, this.context).orElse(null);
        if (atData == null) {
            return null;
        }

        MethodQualifier targetMethod = this.context.legacy().getTargetMethodQualifier();
        AnnotationHandle methodHandle = this.context.legacy().methodAnnotation();
        return this.mixinType.parse(this.context, this.classTarget, targetMethod, atData, methodHandle);
    }
}
