package org.sinytra.adapter.next.flow;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.PatchContext;
import org.sinytra.adapter.next.env.ctx.PatchContextImpl;
import org.sinytra.adapter.next.env.ctx.PatchEnvironment;
import org.sinytra.adapter.next.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.PropertyContainerTemplate;
import org.sinytra.adapter.next.transform.ClassTransformer;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.next.mixin.MixinType;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.next.env.ctx.PatchResult;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.util.AdapterUtil.MIXINPATCH;

public class Patcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /*
    Patcher phases:
    - EARLY - Right after parsing, before preProcess is called
    - AFTER_PRE - After preProcess is called
    - VALIDATED - After clean config is validated 
     */

    // TODO Support all known mixin types from PatchInstance (incl. interface mixins)
    private final PatchEnvironment environment;
    private final List<ClassTransformer> classPatches;
    private final List<MethodTransformer> methodTransformers;

    public Patcher(PatchEnvironment environment, List<ClassTransformer> classPatches, List<MethodTransformer> methodTransformers) {
        this.environment = environment;
        this.classPatches = classPatches;
        this.methodTransformers = methodTransformers;
    }

    public PatchResult apply(ClassNode classNode) {
        // 1. Parse mixin data
        MixinParser.MixinClassHandle mixinClass = MixinParser.parseMixins(classNode, this.environment);
        if (mixinClass == null) return PatchResult.PASS;

        ClassTarget classTarget = mixinClass.classTarget();
        PatchResult result = PatchResult.PASS;
        PatchContextImpl context = new PatchContextImpl(classNode, classTarget.getTypes(), this.environment);

        // 2. Class-level transformations
        for (ClassTransformer patch : this.classPatches) {
            result = result.or(patch.apply(classNode, classTarget, context));
        }

        // 3. Mixin-level transformations 
        for (MixinParser.MixinMethodHandle mixin : mixinClass.mixins()) {
            PatchResult subResult = processMixin(classNode, classTarget, context, mixin);
            result = result.or(subResult);
        }

        context.run();

        return result;
    }

    private PatchResult processMixin(ClassNode classNode, ClassTarget classTarget, PatchContext patchContext, MixinParser.MixinMethodHandle mixin) {
        MethodQualifier target = mixin.properties().getProperty(Keys.TARGET_METHOD).orElse(null);
        if (target == null) return PatchResult.PASS;

        AnnotationHandle atHandle = mixin.methodAnnotation().getNested(MixinAnnotationConstants.PROPERTY_AT).orElse(null);
        MixinType mixinType = mixin.mixinType();
        MixinContext mixinContext = new MixinContext(patchContext, classTarget, classNode, mixin.methodNode(), mixin.methodAnnotation(), atHandle);
        String mixinId = mixinContext.getMixinId();

        // Build base config
        PropertyContainerTemplate template = mixin.mixinType().getConfigurationTemplate();
        MutableConfiguration configuration = MutableConfiguration.create(template);
        configuration.mergeFrom(mixin.properties());

        // << RUN EARLY PHASE TODO

        // 1. Complete and validate clean config
        TxResult preResult = mixinType.preProcess(mixinContext, configuration, mixinContext.getResolvers(), mixinContext.getProcessors());
        if (preResult == TxResult.FAIL) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed preProcess", mixinId);
            return PatchResult.PASS;
        }

        // Validate clean config
        if (!configuration.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            return PatchResult.PASS;
        }

        PatchResult result = PatchResult.PASS;
        // TODO Audit trail
        if (!this.methodTransformers.isEmpty()) {
            this.environment.auditTrail().prepareMethod(mixinContext);
        }

        for (MethodTransformer transformer : this.methodTransformers) {
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            result = result.or(txResult);
            
            /*
            if (txResult == PatchResult.APPLY && this.environment.auditTrail().getMatch(legacy) == PatchAuditTrail.Match.FULL) {
                break;
            }
             */
        }

        return result;
    }
}
