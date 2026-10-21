package org.sinytra.adapter.pipeline;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.api.AuditTrail;
import org.sinytra.adapter.api.MethodPatch;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.ann.SliceData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinType;
import org.sinytra.adapter.pipeline.injector.MixinTypes;
import org.sinytra.adapter.pipeline.manual.MethodPatchResolver;
import org.sinytra.adapter.pipeline.processor.Processor;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolver;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class PipelineMethodTransformer implements MethodTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MethodPatchResolver patchResolver;
    private final boolean patchesOnly;

    public PipelineMethodTransformer(List<MethodPatch> methodPatches, boolean patchesOnly) {
        this.patchResolver = new MethodPatchResolver(methodPatches);
        this.patchesOnly = patchesOnly;
    }

    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        TargetPair cleanTarget = context.cleanMethods().resolve(config.getTargetMethod());
        if (cleanTarget == null) return PatchResult.PASS;

        TargetPair dirtyTarget = context.dirtyMethods().resolve(config.getTargetMethod());
        if (!this.patchResolver.matches(config) && !failsDirtyInjectionCheck(context, config, dirtyTarget) && hasValidSlice(context, config, dirtyTarget))
            return PatchResult.PASS;

        LOGGER.debug(MIXINPATCH, "Considering method {}", context.getMixinId());

        AuditTrail auditTrail = context.environment().auditTrail();
        auditTrail.recordResult(context, config, AuditTrail.Match.NONE);

        PatchResult result = execute(context, config);
        if (result != PatchResult.PASS) {
            auditTrail.recordResult(context, config, AuditTrail.Match.FULL);
            return result;
        }

        return PatchResult.PASS;
    }

    private PatchResult execute(MixinContext context, Configuration config) {
        String mixinId = context.getMixinId();
        Resolvers resolvers = this.patchesOnly ? new Resolvers(false) : context.getResolvers();
        Processors processors = context.getProcessors();

        // 0. Add highest priority manual patch resolver
        resolvers.addFirst(this.patchResolver);

        // 1. Create clean config from validated config
        MutableConfiguration cleanConfig = config.copy();

        // 1.1. Create dirty config
        MutableConfiguration dirtyConfig = cleanConfig.childConfig();
        dirtyConfig.inheritMixinType();
        dirtyConfig.inheritTargetClass();

        Recipe initialRecipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors, context);

        // 2. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            Resolver.ResolutionResult res = context.audited(resolver, () -> resolver.resolve(context, initialRecipe));
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

        Recipe recipe = initialRecipe.withDirtyConfig(dirtyConfig);

        // 3. Complete dirty config
        if (dirtyConfig.hasProperty(ControlKeys.MIXIN_TYPE)) {
            String type = dirtyConfig.getMixinType();
            MixinType lateMixinType = MixinTypes.getMixinType(Type.getType(type).getInternalName());
            if (lateMixinType != null) {
                if (lateMixinType != context.getMixinType()) {
                    cleanConfig.setParameters(lateMixinType.signature().parse(context.methodNode()));
                }
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
            TxResult res = context.audited(processor, () -> processor.process(context, recipe.dirty(), recipe));
            if (res == TxResult.FINALIZE) {
                break;
            }
            if (res == TxResult.FAIL) {
                context.reject(processor, "processor %s failed".formatted(processor.getClass().getSimpleName()));
                return PatchResult.PASS;
            }
        }

        // 5. Check that the patched mixin will apply
        if (!this.patchesOnly && !this.patchResolver.matches(config)) {
            String problem = HandlerValidator.validate(context, recipe.dirty(), recipe);
            if (problem != null) {
                context.reject(this, "validation failed: " + problem);
                return PatchResult.PASS;
            }
        }

        // Remove parameters debug info if invalid due to params having changed
        MethodNode methodNode = context.methodNode();
        if (methodNode.parameters != null
            && !Arrays.equals(Type.getArgumentTypes(methodNode.desc), Type.getArgumentTypes(context.unmodifiedMethodNode().desc))
        ) {
            methodNode.parameters = null;
        }

        return PatchResult.APPLY;
    }

    public boolean failsDirtyInjectionCheck(MixinContext context, Configuration config, TargetPair dirtyTarget) {
        return !context.getMixinType().canInject(context, config)
            || dirtyTarget == null
            || !context.injections().matches(dirtyTarget)
            && !context.injections().matches(dirtyTarget, InjectionQuery.constant());
    }

    public boolean hasValidSlice(MixinContext context, Configuration config, @Nullable TargetPair target) {
        if (target == null)
            return false;

        List<SliceData> slices = config.getProperty(MixinKeys.SLICE)
            .map(List::of)
            .or(() -> config.getProperty(MixinKeys.SLICES))
            .orElse(List.of());
        for (SliceData slice : slices) {
            AtData from = slice.from();
            if (from != null && !context.injections().matches(target, InjectionQuery.sliceBoundary(from)))
                return false;

            AtData to = slice.to();
            if (to != null && !context.injections().matches(target, InjectionQuery.sliceBoundary(to)))
                return false;
        }
        return true;
    }
}
