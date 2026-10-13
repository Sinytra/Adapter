package org.sinytra.adapter.pipeline;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.*;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotationConstants;
import org.sinytra.adapter.pipeline.MixinParser.MixinMethodHandle;
import org.sinytra.adapter.pipeline.audit.MixinGroups;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.ctx.PatchContextImpl;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.injector.MixinType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class PatcherImpl implements Patcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    // TODO Support all known mixin types from PatchInstance (incl. interface mixins)
    private final PatchEnvironment environment;
    private final List<ClassTransformer> classPatches;
    private final Multimap<TxPhase, MethodTransformer> methodTransformers;

    public PatcherImpl(PatchEnvironment environment, List<ClassTransformer> classPatches, Multimap<TxPhase, MethodTransformer> methodTransformers) {
        this.environment = environment;
        this.classPatches = classPatches;
        this.methodTransformers = methodTransformers;
    }

    @Override
    public PatchResult process(ClassNode classNode) {
        // Parse class data
        ClassTarget classTarget = MixinParser.prepareMixinClass(classNode, this.environment);
        if (classTarget == null || classTarget.getTypes().isEmpty()) return PatchResult.PASS;

        PatchResult result = PatchResult.PASS;
        PatchContextImpl context = new PatchContextImpl(classNode, classTarget, classTarget.getTypes(), this.environment);

        // Class-level transformations
        for (ClassTransformer patch : this.classPatches) {
            result = result.or(patch.apply(classNode, context));
        }

        // Parse mixin methods
        // Note: Parsing is split in two to account for potential changes to mixin methods by ClassTransformers
        MixinParser.MixinClassHandle mixinClass = MixinParser.parseMixins(classTarget, classNode, this.environment);
        if (mixinClass == null) return result;

        // Mixin-level transformations with one handler per target method
        List<MixinParser.MixinMethodHandle> mixins = new ArrayList<>();
        for (MixinParser.MixinMethodHandle mixin : mixinClass.mixins()) {
            mixins.addAll(MultiTargetSplitter.split(classNode, classTarget, mixin, this.environment));
        }
        for (MixinParser.MixinMethodHandle mixin : mixins) {
            PatchResult subResult = processMixin(classNode, classTarget, context, mixin);
            result = result.or(subResult);
        }

        context.run();
        MixinGroups groups = MixinGroups.create(mixins.stream().map(MixinMethodHandle::methodNode).toList());
        this.environment.auditTrail().processGroups(classNode, groups);

        return result;
    }

    private PatchResult processMixin(ClassNode classNode, ClassTarget classTarget, PatchContextImpl patchContext, MixinParser.MixinMethodHandle mixin) {
        MethodQualifier target = mixin.properties().getProperty(MixinKeys.TARGET_METHOD).orElse(null);
        if (target == null) return PatchResult.PASS;

        // Prepare context
        AnnotationHandle atHandle = mixin.methodAnnotation().getNested(MixinAnnotationConstants.PROPERTY_AT).orElse(null);
        MixinType mixinType = mixin.mixinType();
        Set<MixinFlag> flags = mixinType.getFlags();
        // Build base config
        PropertyContainerTemplate template = mixin.mixinType().getConfigurationTemplate();
        MutableConfiguration configuration = MutableConfiguration.create(template);
        configuration.mergeFrom(mixin.properties());

        MixinContext mixinContext = new MixinContext(mixinType, patchContext, classTarget, classNode, mixin.methodNode(), mixin.methodAnnotation(), atHandle, flags, configuration);
        String mixinId = mixinContext.getMixinId();

        this.environment.auditTrail().prepareMethod(mixinContext);

        PatchContextImpl.Checkpoint checkpoint = patchContext.checkpoint();
        PatchResult result;
        try {
            result = applyPatches(mixinContext, configuration);
        } catch (Exception e) {
            LOGGER.error(MIXINPATCH, "Error patching mixin {}, reverting changes", mixinId, e);
            rollback(classNode, patchContext, checkpoint, mixinContext);
            this.environment.auditTrail().recordResult(mixinContext, configuration, AuditTrail.Match.NONE);
            return PatchResult.PASS;
        }

        if (mixinContext.getRejection() != null) {
            LOGGER.debug(MIXINPATCH, "Rejected patch of mixin {}, reverting changes: {}", mixinId, mixinContext.getRejection());
            rollback(classNode, patchContext, checkpoint, mixinContext);
            this.environment.auditTrail().recordRejection(mixinContext, configuration);
            return PatchResult.PASS;
        }
        return result;
    }

    private static void rollback(ClassNode classNode, PatchContextImpl patchContext, PatchContextImpl.Checkpoint checkpoint, MixinContext mixinContext) {
        int index = classNode.methods.indexOf(mixinContext.methodNode());
        if (index != -1) {
            classNode.methods.set(index, mixinContext.unmodifiedMethodNode());
        }
        patchContext.rollback(checkpoint);
    }

    private PatchResult applyPatches(MixinContext mixinContext, MutableConfiguration configuration) {
        MixinType mixinType = mixinContext.getMixinType();
        String mixinId = mixinContext.getMixinId();

        // << RUN EARLY PHASE
        PatchResult result = runPhase(TxPhase.EARLY, mixinContext);

        // Complete clean config
        TxResult preResult = mixinType.preProcess(mixinContext, configuration, mixinContext.getResolvers());
        if (preResult == TxResult.FAIL) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed preProcess", mixinId);
            return PatchResult.PASS;
        }

        // << RUN LOADED PHASE
        result = result.or(runPhase(TxPhase.LOADED, mixinContext));

        // Validate clean config
        if (!configuration.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            return PatchResult.PASS;
        }
        
        // Temporarily set this to a high number for frame analysis to work
        // Will be set correctly by ClassWriter after patching
        MethodNode methodNode = mixinContext.methodNode();
        methodNode.maxLocals = methodNode.maxStack = 999;

        return result.or(runPhase(TxPhase.VALIDATED, mixinContext));
    }

    private PatchResult runPhase(TxPhase phase, MixinContext mixinContext) {
        PatchResult result = PatchResult.PASS;
        for (MethodTransformer transformer : this.methodTransformers.get(phase)) {
            result = result.or(mixinContext.audited(transformer, () -> transformer.apply(mixinContext)));
        }
        return result;
    }
}
