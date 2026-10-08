package org.sinytra.adapter.patch;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.MixinGroups;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.*;
import org.sinytra.adapter.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.patch.MixinParser.MixinMethodHandle;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.mixin.MixinFlag;
import org.sinytra.adapter.patch.mixin.MixinType;
import org.sinytra.adapter.transform.ClassTransformer;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class Patcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    // TODO Support all known mixin types from PatchInstance (incl. interface mixins)
    private final PatchEnvironment environment;
    private final List<ClassTransformer> classPatches;
    private final Multimap<TxPhase, MethodTransformer> methodTransformers;

    private Patcher(PatchEnvironment environment, List<ClassTransformer> classPatches, Multimap<TxPhase, MethodTransformer> methodTransformers) {
        this.environment = environment;
        this.classPatches = classPatches;
        this.methodTransformers = methodTransformers;
    }

    public PatchResult process(ClassNode classNode) {
        // Parse class data
        ClassTarget classTarget = MixinParser.prepareMixinClass(classNode, this.environment);

        PatchResult result = PatchResult.PASS;
        PatchContextImpl context = new PatchContextImpl(classNode, classTarget.getTypes(), this.environment);

        // Class-level transformations
        for (ClassTransformer patch : this.classPatches) {
            result = result.or(patch.apply(classNode, classTarget, context));
        }

        // Parse mixin methods
        // Note: Parsing is split in two to account for potential changes to mixin methods by ClassTransformers
        MixinParser.MixinClassHandle mixinClass = MixinParser.parseMixins(classTarget, classNode, this.environment);
        if (mixinClass == null) return result;

        // Mixin-level transformations 
        List<MixinParser.MixinMethodHandle> failedMixins = new ArrayList<>();
        for (MixinParser.MixinMethodHandle mixin : mixinClass.mixins()) {
            PatchResult subResult = processMixin(classNode, classTarget, context, mixin, failedMixins);
            result = result.or(subResult);
        }

        if (!failedMixins.isEmpty() && this.environment.stripFailingMixins()) {
            result = result.or(stripFailedMixins(classNode, failedMixins));
        }

        context.run();
        MixinGroups groups = MixinGroups.create(mixinClass.mixins().stream().map(MixinMethodHandle::methodNode).toList());
        this.environment.auditTrail().processGroups(classNode, groups);

        // Must run last: context.run() can still add parameter annotations, and ASM sizes their arrays from
        // the descriptor as it stood when the array was created, so they can end up shorter.
        if (normalizeParameterAnnotations(classNode)) {
            result = result.or(PatchResult.COMPUTE_FRAMES);
        }

        return result;
    }

    /** See PipelineMethodTransformer#skip: every give-up exit must record the mixin, or the jar is reported as compatible. */
    private static PatchResult skip(MixinContext context, org.sinytra.adapter.patch.config.Configuration config, String reason) {
        context.environment().auditTrail().recordResult(context, config, org.sinytra.adapter.env.ctx.AuditTrail.Match.NONE);
        // These early exits run outside any pushAudit/popAudit frame, so open one to report the reason.
        context.pushAudit(SKIP_REASON_FRAME);
        try {
            context.recordCtxAudit("Skipped mixin: %s", reason);
        } catch (RuntimeException ignored) {
            // still best-effort: recordResult above is what makes the skip itself visible
        } finally {
            context.popAudit();
        }
        return PatchResult.PASS;
    }

    /** Audit frame marker for give-up exits; its {@code toString} is what the report shows. */
    private static final Object SKIP_REASON_FRAME = new Object() {
        @Override
        public String toString() {
            return "skip";
        }
    };

    private PatchResult processMixin(ClassNode classNode, ClassTarget classTarget, PatchContext patchContext, MixinParser.MixinMethodHandle mixin, List<MixinParser.MixinMethodHandle> failed) {
        MixinType handlerType = mixin.mixinType();

        // Types without a method target (@Accessor) carry no TARGET_METHOD; send them to their own validation.
        if (!handlerType.targetsMethod()) {
            MixinContext nonMethodContext = new MixinContext(handlerType, patchContext, classTarget, classNode,
                mixin.methodNode(), mixin.methodAnnotation(), null, handlerType.getFlags());
            this.environment.auditTrail().prepareMethod(nonMethodContext);

            MutableConfiguration nonMethodConfig = MutableConfiguration.create(handlerType.getConfigurationTemplate());
            nonMethodConfig.mergeFrom(mixin.properties());

            handlerType.validateNonMethodTarget(nonMethodContext, nonMethodConfig);
            return PatchResult.PASS;
        }

        MethodQualifier target = mixin.properties().getProperty(MixinKeys.TARGET_METHOD).orElse(null);
        if (target == null) return PatchResult.PASS;

        // Prepare context
        AnnotationHandle atHandle = mixin.methodAnnotation().getNested(MixinAnnotationConstants.PROPERTY_AT).orElse(null);
        MixinType mixinType = mixin.mixinType();
        Set<MixinFlag> flags = mixinType.getFlags();
        MixinContext mixinContext = new MixinContext(mixinType, patchContext, classTarget, classNode, mixin.methodNode(), mixin.methodAnnotation(), atHandle, flags);
        String mixinId = mixinContext.getMixinId();

        // Build base config
        PropertyContainerTemplate template = mixin.mixinType().getConfigurationTemplate();
        MutableConfiguration configuration = MutableConfiguration.create(template);
        configuration.mergeFrom(mixin.properties());

        this.environment.auditTrail().prepareMethod(mixinContext);

        PatchResult result = PatchResult.PASS;
        // << RUN EARLY PHASE
        for (MethodTransformer transformer : getTransformers(TxPhase.EARLY)) {
            mixinContext.pushAudit(transformer);
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            mixinContext.popAudit();

            result = result.or(txResult);
        }

        // Complete clean config
        TxResult preResult = mixinType.preProcess(mixinContext, configuration, mixinContext.getResolvers(), mixinContext.getProcessors());
        if (preResult == TxResult.FAIL) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed preProcess", mixinId);
            failed.add(mixin);
            return skip(mixinContext, configuration, "failed preProcess");
        }

        // << RUN LOADED PHASE
        for (MethodTransformer transformer : getTransformers(TxPhase.LOADED)) {
            mixinContext.pushAudit(transformer);
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            mixinContext.popAudit();
            result = result.or(txResult);
        }

        // Validate clean config
        if (!configuration.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            failed.add(mixin);
            return skip(mixinContext, configuration, "invalid CLEAN config");
        }
        
        // Temporarily set this to a high number for frame analysis to work
        // Will be set correctly by ClassWriter after patching
        MethodNode methodNode = mixinContext.methodNode();
        methodNode.maxLocals = methodNode.maxStack = 999;

        for (MethodTransformer transformer : getTransformers(TxPhase.VALIDATED)) {
            mixinContext.pushAudit(transformer);
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            mixinContext.popAudit();
            result = result.or(txResult);
        }

        // A "locals = LocalCapture..." list is compiled against the injection site as it was when the mod was
        // built, and Mixin aborts at runtime when it no longer lines up. A handler without the mark set by
        // LocalCaptureUpgradeTransformer is failed via the failure list, as Match.or() would overwrite a NONE.
        if (configuration.hasProperty(MixinKeys.LOCALS)
            && !configuration.hasProperty(SpecialKeys.LOCALS_UPGRADED)) {
            LOGGER.debug(MIXINPATCH, "Mixin {} captures locals that could not be verified; it would abort at runtime", mixinId);
            failed.add(mixin);
        }

        // A resolver failure records Match.NONE rather than throwing, so the audit trail is the only witness.
        if (this.environment.auditTrail().getMatch(mixinContext) == AuditTrail.Match.NONE) {
            failed.add(mixin);
        }

        return result;
    }

    /** Removes handlers that could not be adapted so Mixin never registers them; only when nothing calls them. */
    private PatchResult stripFailedMixins(ClassNode classNode, List<MixinParser.MixinMethodHandle> failed) {
        boolean removedAny = false;
        for (MixinParser.MixinMethodHandle mixin : failed) {
            MethodNode method = mixin.methodNode();
            if (isReferenced(classNode, method)) {
                LOGGER.debug(MIXINPATCH, "Not stripping {}#{}, it is referenced by other code in the class",
                    classNode.name, method.name);
                continue;
            }
            if (classNode.methods.remove(method)) {
                removedAny = true;
                LOGGER.warn(MIXINPATCH, "Removed unadaptable mixin handler {}#{}{} - the mod loads with this feature disabled",
                    classNode.name, method.name, method.desc);
            }
        }
        // Removing methods invalidates any previously computed frames.
        return removedAny ? PatchResult.COMPUTE_FRAMES : PatchResult.PASS;
    }

    /**
     * Keeps a method's parameter-annotation arrays and counts in sync with its descriptor; a stale count
     * aborts the whole mod's transform inside ASM's {@code MethodWriter}.
     */
    private static boolean normalizeParameterAnnotations(ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            int paramCount = Type.getArgumentTypes(method.desc).length;

            List<AnnotationNode>[] visible = resizeParameterAnnotations(method.visibleParameterAnnotations, paramCount);
            if (visible != method.visibleParameterAnnotations) {
                method.visibleParameterAnnotations = visible;
                changed = true;
            }
            List<AnnotationNode>[] invisible = resizeParameterAnnotations(method.invisibleParameterAnnotations, paramCount);
            if (invisible != method.invisibleParameterAnnotations) {
                method.invisibleParameterAnnotations = invisible;
                changed = true;
            }

            int visibleCount = visible == null ? 0 : visible.length;
            if (method.visibleAnnotableParameterCount != visibleCount) {
                method.visibleAnnotableParameterCount = visibleCount;
                changed = true;
            }
            int invisibleCount = invisible == null ? 0 : invisible.length;
            if (method.invisibleAnnotableParameterCount != invisibleCount) {
                method.invisibleAnnotableParameterCount = invisibleCount;
                changed = true;
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] resizeParameterAnnotations(List<AnnotationNode>[] annotations, int paramCount) {
        if (annotations == null || annotations.length == paramCount) {
            return annotations;
        }
        List<AnnotationNode>[] resized = (List<AnnotationNode>[]) new List<?>[paramCount];
        System.arraycopy(annotations, 0, resized, 0, Math.min(annotations.length, paramCount));
        return resized;
    }

    private static boolean isReferenced(ClassNode classNode, MethodNode method) {
        for (MethodNode other : classNode.methods) {
            if (other == method) continue;
            for (AbstractInsnNode insn : other.instructions) {
                if (insn instanceof MethodInsnNode call
                    && call.name.equals(method.name)
                    && call.desc.equals(method.desc)
                    && call.owner.equals(classNode.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Collection<MethodTransformer> getTransformers(TxPhase phase) {
        return this.methodTransformers.get(phase);
    }

    public static Builder builder(PatchEnvironment environment) {
        return new Builder(environment);
    }

    public static class Builder {
        private final PatchEnvironment environment;
        private final List<ClassTransformer> classTransformers = new ArrayList<>();
        private final Multimap<TxPhase, MethodTransformer> methodTransformers = HashMultimap.create();

        public Builder(PatchEnvironment environment) {
            this.environment = environment;
        }

        public Builder classTransformer(ClassTransformer transformer) {
            this.classTransformers.add(transformer);
            return this;
        }

        public Builder classTransformers(List<ClassTransformer> transformers) {
            this.classTransformers.addAll(transformers);
            return this;
        }

        public Builder methodTransformer(TxPhase phase, MethodTransformer transformer) {
            this.methodTransformers.put(phase, transformer);
            return this;
        }

        public Builder methodTransformers(Multimap<TxPhase, MethodTransformer> transformers) {
            this.methodTransformers.putAll(transformers);
            return this;
        }

        public Patcher build() {
            return new Patcher(this.environment, this.classTransformers, this.methodTransformers);
        }
    }
}

