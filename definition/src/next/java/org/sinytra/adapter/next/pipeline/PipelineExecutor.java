package org.sinytra.adapter.next.pipeline;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.config.*;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.next.pipeline.resolver.Resolver.ResolutionResult;
import org.sinytra.adapter.next.pipeline.resolver.Resolver.ResultType;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.next.type.MixinTypes;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PipelineExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClassTarget classTarget;
    private final MixinContext context;

    public PipelineExecutor(ClassTarget classTarget, MixinContext context) {
        this.classTarget = classTarget;
        this.context = context;
    }

    public Patch.Result execute(MixinType mixinType) {
        MixinData data = parseMixinData(mixinType);
        if (data == null) {
            return Patch.Result.PASS;
        }
        String mixinId = this.context.classNode().name + "#" + this.context.methodNode().name + this.context.methodNode().desc;

        Resolvers resolvers = new Resolvers();
        Processors processors = new Processors();
        PropertyContainerTemplate template = Objects.requireNonNull(mixinType.getConfigurationTemplate());

        // 1. Create clean config
        ConfigurationImpl cleanConfig = new ConfigurationImpl(template);
        cleanConfig.setMixinType(this.context.methodAnnotation().getDesc());
        cleanConfig.setTargetClass(data.getTargetClass());
        cleanConfig.setTargetMethod(data.getTargetMethod());
        cleanConfig.setAtData(data.at());
        cleanConfig.setReturnType(Type.getReturnType(context.methodNode().desc));

        // 1.1. Create dirty config
        MutableConfiguration dirtyConfig = new ConfigurationImpl(template, cleanConfig);
        dirtyConfig.inheritMixinType();
        dirtyConfig.inheritTargetClass();

        Recipe recipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors, this.context);

        // 2. Complete clean config
        TxResult preResult = mixinType.preProcess(data, this.context, cleanConfig, recipe);
        if (preResult == TxResult.FAIL) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed preProcess", mixinId);
            return Patch.Result.PASS;
        }

        // 2.1. Validate clean config
        if (!cleanConfig.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            return Patch.Result.PASS;
        }

        // 3. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            ResolutionResult res = resolver.resolve(data, this.context, recipe);
            Objects.requireNonNull(res, "BUG: Received null from resolver " + resolver.getClass());

            if (res.type() == ResultType.SUCCESS || res.type() == ResultType.REPLACE) {
                if (res.type() == ResultType.REPLACE) {
                    dirtyConfig = res.patch().copy();
                    break;
                } else {
                    dirtyConfig.mergeFrom(res.patch());
                }
            } else if (res.type() == ResultType.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed RESOLVER {}", mixinId, resolver.getClass().getSimpleName());
                return Patch.Result.PASS;
            }
        }

        // 4. Complete dirty config
        if (dirtyConfig.hasProperty(Configuration.Keys.MIXIN_TYPE)) {
            String type = dirtyConfig.getMixinType();
            MixinType lateMixinType = MixinTypes.getMixinType(Type.getType(type).getInternalName());
            if (lateMixinType != null) {
                TxResult postResult = lateMixinType.postProcess(data, this.context, cleanConfig, dirtyConfig, recipe);
                if (postResult == TxResult.FAIL) {
                    LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed postProcess", mixinId);
                    return Patch.Result.PASS;
                }
            }
        }

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
    private MixinData parseMixinData(MixinType mixinType) {
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

        Set<PropertyKey<?>> keys = mixinType.requestProperties();
        BasePropertyContainer properties = new BasePropertyContainer();
        for (PropertyKey key : keys) {
            Object value = methodHandle.getValue(key.name()).map(AnnotationValueHandle::get).orElse(null);
            if (value == null) continue;

            if (key.parser() != null) {
                Object parsed = key.parser().parse(value, this.context);
                properties.setProperty(key, parsed);
            } else {
                throw new IllegalStateException("Cannot parse for key %s, it does not define a parser".formatted(key.name()));
            }
        }

        return new MixinData(this.classTarget, targetMethod, atData, properties);
    }
}
