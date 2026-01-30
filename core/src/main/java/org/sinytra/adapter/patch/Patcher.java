package org.sinytra.adapter.patch;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.*;
import org.sinytra.adapter.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.patch.config.key.MixinKeys;
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
        // Parse mixin data
        MixinParser.MixinClassHandle mixinClass = MixinParser.parseMixins(classNode, this.environment);
        if (mixinClass == null) return PatchResult.PASS;

        ClassTarget classTarget = mixinClass.classTarget();
        PatchResult result = PatchResult.PASS;
        PatchContextImpl context = new PatchContextImpl(classNode, classTarget.getTypes(), this.environment);

        // Class-level transformations
        for (ClassTransformer patch : this.classPatches) {
            result = result.or(patch.apply(classNode, classTarget, context));
        }

        // Mixin-level transformations 
        for (MixinParser.MixinMethodHandle mixin : mixinClass.mixins()) {
            PatchResult subResult = processMixin(classNode, classTarget, context, mixin);
            result = result.or(subResult);
        }

        context.run();

        return result;
    }

    private PatchResult processMixin(ClassNode classNode, ClassTarget classTarget, PatchContext patchContext, MixinParser.MixinMethodHandle mixin) {
        MethodQualifier target = mixin.properties().getProperty(MixinKeys.TARGET_METHOD).orElse(null);
        if (target == null) return PatchResult.PASS;

        // Prepare context
        AnnotationHandle atHandle = mixin.methodAnnotation().getNested(MixinAnnotationConstants.PROPERTY_AT).orElse(null);
        MixinType mixinType = mixin.mixinType();
        Set<MixinFlag> flags = mixinType.getFlags();
        MixinContext mixinContext = new MixinContext(patchContext, classTarget, classNode, mixin.methodNode(), mixin.methodAnnotation(), atHandle, flags);
        String mixinId = mixinContext.getMixinId();

        // Build base config
        PropertyContainerTemplate template = mixin.mixinType().getConfigurationTemplate();
        MutableConfiguration configuration = MutableConfiguration.create(template);
        configuration.mergeFrom(mixin.properties());

        PatchResult result = PatchResult.PASS;
        // << RUN EARLY PHASE
        for (MethodTransformer transformer : getTransformers(TxPhase.EARLY)) {
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            result = result.or(txResult);
        }

        // Complete clean config
        TxResult preResult = mixinType.preProcess(mixinContext, configuration, mixinContext.getResolvers(), mixinContext.getProcessors());
        if (preResult == TxResult.FAIL) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to failed preProcess", mixinId);
            return PatchResult.PASS;
        }
        
        // << RUN LOADED PHASE
        for (MethodTransformer transformer : getTransformers(TxPhase.LOADED)) {
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            result = result.or(txResult);
        }

        // Validate clean config
        if (!configuration.validate()) {
            LOGGER.debug(MIXINPATCH, "Skipping mixin {} due to invalid CLEAN config", mixinId);
            return PatchResult.PASS;
        }

        // TODO Audit trail
        this.environment.auditTrail().prepareMethod(mixinContext);

        for (MethodTransformer transformer : getTransformers(TxPhase.VALIDATED)) {
            PatchResult txResult = transformer.apply(mixinContext, configuration);
            result = result.or(txResult);
        }

        return result;
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
