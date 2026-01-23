package org.sinytra.adapter.next.pipeline;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.next.type.MixinTypes;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.api.PatchResult;
import org.sinytra.adapter.patch.api.TargetPair;
import org.slf4j.Logger;

import java.util.Objects;

import static org.sinytra.adapter.patch.util.AdapterUtil.MIXINPATCH;

public class PipelineMethodTransformer implements MethodTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), config.getTargetMethod());
        if (cleanTarget == null)
            return PatchResult.PASS;

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), config.getTargetMethod());
        if (!context.legacy().failsDirtyInjectionCheck() && context.legacy().hasValidSlice(dirtyTarget))
            return PatchResult.PASS;

        PatchAuditTrail.Match previousMatch = context.environment().auditTrail().getMatch(context.legacy());
        if (previousMatch != null && previousMatch != PatchAuditTrail.Match.NONE)
            return PatchResult.PASS;

        String annotationInternalName = Type.getType(context.methodAnnotation().getDesc()).getInternalName();
        MixinType mixinType = MixinTypes.getMixinType(annotationInternalName);
        if (mixinType == null) return PatchResult.PASS;

        ClassNode cls = context.classNode();
        LOGGER.debug(MIXINPATCH, "Considering method {}.{}", cls.name, cls.name);

        PatchAuditTrail auditTrail = context.environment().auditTrail();
        auditTrail.recordResult(context.legacy(), PatchAuditTrail.Match.NONE);

        PatchResult result = execute(context, config);
        if (result != PatchResult.PASS) {
            auditTrail.recordResult(context.legacy(), PatchAuditTrail.Match.FULL);
            return result;
        }

        return PatchResult.PASS;
    }

    private PatchResult execute(MixinContext context, Configuration config) {
        String mixinId = context.getMixinId();
        Resolvers resolvers = context.getResolvers();
        Processors processors = context.getProcessors();

        // 1. Create clean config from validated config
        MutableConfiguration cleanConfig = config.copy();

        // 1.1. Create dirty config
        MutableConfiguration dirtyConfig = cleanConfig.childConfig();
        dirtyConfig.inheritMixinType();
        dirtyConfig.inheritTargetClass();

        Recipe recipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors, context);

        // 2. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            Resolver.ResolutionResult res = resolver.resolve(context, recipe);
            Objects.requireNonNull(res, "BUG: Received null from resolver " + resolver.getClass());

            if (res.type() == Resolver.ResultType.SUCCESS || res.type() == Resolver.ResultType.REPLACE) {
                if (res.type() == Resolver.ResultType.REPLACE) {
                    dirtyConfig = res.patch().copy();
                    break;
                } else {
                    dirtyConfig.mergeFrom(res.patch());
                }
            } else if (res.type() == Resolver.ResultType.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed RESOLVER {}", mixinId, resolver.getClass().getSimpleName());
                return PatchResult.PASS;
            }
        }

        // 3. Complete dirty config
        if (dirtyConfig.hasProperty(Keys.MIXIN_TYPE)) {
            String type = dirtyConfig.getMixinType();
            MixinType lateMixinType = MixinTypes.getMixinType(Type.getType(type).getInternalName());
            if (lateMixinType != null) {
                TxResult postResult = lateMixinType.postProcess(context, cleanConfig, dirtyConfig, recipe);
                if (postResult == TxResult.FAIL) {
                    LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed postProcess", mixinId);
                    return PatchResult.PASS;
                }
            }
        }

        // 3.1. Validate dirty config
        if (!dirtyConfig.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid DIRTY config", mixinId);
            return PatchResult.PASS;
        }

        // 4. Run Processors
        processors.freeze();
        for (Processor processor : processors.getAll()) {
            TxResult res = processor.process(context, dirtyConfig, recipe);
            if (res == TxResult.FINALIZE) {
                break;
            }
            if (res == TxResult.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed PROCESSOR {}", mixinId, processor.getClass().getSimpleName());
                return PatchResult.PASS;
            }
        }

        return PatchResult.APPLY;
    }
}
