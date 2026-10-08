package org.sinytra.adapter.transform;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ann.SliceData;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.mixin.MixinType;
import org.sinytra.adapter.patch.mixin.MixinTypes;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.adapter.transform.patch.MethodPatchResolver;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;

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
        TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), config.getTargetMethod());
        if (cleanTarget == null) {
            // The target class can exist only in the patched view: supplied by the mod, by one of its
            // libraries, or by the platform. Mod-supplied classes never need adaptation; platform classes pass
            // only when the injection point already resolves in the patched class.
            String targetClass = config.getProperty(ControlKeys.TARGET_CLASS).orElse(null);
            TargetPair dirtyCandidate = context.methods().findOwnMethodPair(context.dirtyLookup(), config.getTargetMethod());

            boolean modSupplied = !isPlatformClass(targetClass);
            boolean platformButAlreadyResolvable = !modSupplied
                && dirtyCandidate != null
                && context.methods().hasInjectionTargetInsns(dirtyCandidate);

            if (targetClass != null
                && context.dirtyLookup().getClass(targetClass).isPresent()
                && context.cleanLookup().getClass(targetClass).isEmpty()
                && (modSupplied || platformButAlreadyResolvable)) {
                context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
                context.setEffectiveInjectionPoint(config.getAtData(), config.getTargetMethod());
                try {
                    context.recordCtxAudit("No adaptation required: target class %s is not in the vanilla jar (%s)",
                        targetClass, modSupplied ? "supplied by the mod itself" : "injection point already resolves");
                } catch (RuntimeException ignored) {
                    // no audit frame at this exit
                }
                return PatchResult.PASS;
            }

            // Publish the injection point: later stages need it, and leaving it unset disables capture re-derivation.
            context.setEffectiveInjectionPoint(config.getAtData(), config.getTargetMethod());
            return skip(context, config, "target method not found in the clean (vanilla) class");
        }

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), config.getTargetMethod());
        if (!this.patchResolver.matches(config) && !failsDirtyInjectionCheck(context, config, dirtyTarget) && hasValidSlice(context, config, dirtyTarget)) {
            // Not a failure: the mixin already injects into the patched target as-is.
            context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
            // It resolves here, and captured-local transformers need the site even when nothing was rewritten.
            context.setEffectiveInjectionPoint(config.getAtData(), config.getTargetMethod());
            try {
                context.recordCtxAudit("No adaptation required: injection point already resolves in the patched target");
            } catch (RuntimeException ignored) {
                // no audit frame at this exit
            }
            return PatchResult.PASS;
        }

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

    /** Records a give-up exit so the mixin shows up in the audit trail instead of vanishing from the report. */
    private static PatchResult skip(MixinContext context, Configuration config, String reason, Object... args) {
        context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.NONE);
        try {
            context.recordCtxAudit("Skipped mixin: " + reason, args);
        } catch (RuntimeException ignored) {
            // Not every exit runs inside an audit frame; the reason is best-effort.
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

        Recipe recipe = new Recipe(cleanConfig, dirtyConfig, resolvers, processors, context);

        // 2. Run Resolvers
        resolvers.freeze();
        for (Resolver resolver : resolvers.getAll()) {
            context.pushAudit(resolver);
            Resolver.ResolutionResult res = resolver.resolve(context, recipe);
            context.popAudit();
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
                return skip(context, config, "failed resolver %s", resolver.getClass().getSimpleName());
            }
        }

        // 3. Complete dirty config
        if (dirtyConfig.hasProperty(ControlKeys.MIXIN_TYPE)) {
            String type = dirtyConfig.getMixinType();
            MixinType lateMixinType = MixinTypes.getMixinType(Type.getType(type).getInternalName());
            if (lateMixinType != null) {
                TxResult postResult = lateMixinType.postProcess(context, cleanConfig, dirtyConfig, recipe);
                if (postResult == TxResult.FAIL) {
                    LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed postProcess", mixinId);
                    return skip(context, config, "failed postProcess");
                }
            }
        }

        // 3.1. Validate dirty config
        if (!dirtyConfig.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid DIRTY config", mixinId);
            return skip(context, config, "invalid DIRTY config");
        }

        // 4. Run Processors
        processors.freeze();
        for (Processor processor : processors.getAll()) {
            context.pushAudit(processor);
            TxResult res = processor.process(context, dirtyConfig, recipe);
            context.popAudit();
            if (res == TxResult.FINALIZE) {
                break;
            }
            if (res == TxResult.FAIL) {
                LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed PROCESSOR {}", mixinId, processor.getClass().getSimpleName());
                return skip(context, config, "failed processor %s", processor.getClass().getSimpleName());
            }
        }

        // Remove parameters debug info if invalid due to params having changed
        if (context.methodNode().parameters != null
            && Type.getArgumentCount(context.methodNode().desc) != context.methodNode().parameters.size()
        ) {
            context.methodNode().parameters = null;
        }

        // Publish where the injection point ended up; later transformers need the effective site.
        context.setEffectiveInjectionPoint(dirtyConfig.getAtData(), dirtyConfig.getTargetMethod());

        return PatchResult.APPLY;
    }

    public boolean failsDirtyInjectionCheck(MixinContext context, Configuration config, TargetPair dirtyTarget) {
        return !context.getMixinType().canInject(context, config)
            || dirtyTarget == null
            || !context.methods().hasInjectionTargetInsns(dirtyTarget)
            && computeConstantTargetInsns(context, dirtyTarget).isEmpty();
    }

    // TODO Clean up
    public List<AbstractInsnNode> computeConstantTargetInsns(MixinContext context, @Nullable TargetPair target) {
        return context.methods().computeInjectionTargetInsns(
            target,
            () -> context.methodAnnotation().getNested("constant").orElse(null),
            (ctx, h) -> new BeforeConstant(ctx, h.unwrap(), Type.getReturnType(context.methodNode().desc).getDescriptor()),
            false
        );
    }

    public boolean hasValidSlice(MixinContext context, Configuration config, @Nullable TargetPair target) {
        if (target == null)
            return false;

        SliceData slice = config.getProperty(MixinKeys.SLICE).orElse(null);
        if (slice == null) return true;

        AtData from = slice.from();
        if (from != null && !validateAtNode(context, from, target))
            return false;

        AtData to = slice.to();
        return to == null || validateAtNode(context, to, target);
    }

    private boolean validateAtNode(MixinContext context, AtData at, TargetPair target) {
        List<AbstractInsnNode> insns = context.methods().computeInjectionTargetInsns(
            target,
            context::injectionPointAnnotation,
            (ctx, h) -> InjectionPoint.parse(ctx, context.methodNode(), context.methodAnnotation().unwrap(), at.toAnnotationNode()),
            false,
            true
        );
        return !insns.isEmpty();
    }

    /** Whether the name belongs to a platform namespace (game or mod loader) rather than to a mod. */
    private static boolean isPlatformClass(String internalName) {
        return internalName.startsWith("net/minecraft/")
            || internalName.startsWith("net/neoforged/")
            || internalName.startsWith("com/mojang/")
            || internalName.startsWith("net/fabricmc/")
            || internalName.startsWith("org/spongepowered/");
    }
}








