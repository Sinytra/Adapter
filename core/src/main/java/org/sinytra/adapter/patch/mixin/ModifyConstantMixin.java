package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ann.ConstantData;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.ConfigurationTemplates;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.spongepowered.asm.mixin.injection.points.BeforeConstant;

import java.util.List;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.SINGLE_ANY;

/**
 * Handles {@code @ModifyConstant} mixins, which carry no {@code @At}: the injection point comes from the
 * {@code @Constant} annotation. Handlers whose constant no longer resolves fail in {@link #postProcess}, so
 * they are dropped and reported rather than left in the jar to abort at runtime.
 */
public class ModifyConstantMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_BASE.extend()
        .keys(MixinKeys.TARGET_CONSTANT, MixinKeys.ORDINAL)
        .pluralKeys(MixinKeys.SLICES)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY)));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        dirty.inheritProperyIfAbsent(MixinKeys.ORDINAL);

        // PropertyProcessor only forwards keys that are already set, so TARGET_CONSTANT has to be read off the
        // annotation here; without it the dirty config fails validation and the mixin is dropped.
        if (!dirty.hasProperty(MixinKeys.TARGET_CONSTANT)) {
            context.methodAnnotation().getNested("constant")
                .flatMap(ConstantData::parse)
                .ifPresent(constant -> dirty.setProperty(MixinKeys.TARGET_CONSTANT, constant));
        }

        // MIXIN_BASE.require() mandates PARAMETERS and RETURN_TYPE, which every mixin type populates here.
        dirty.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY)));
        dirty.setReturnType(Type.getReturnType(context.methodNode().desc));

        if (dirty.getTargetMethod() == null) {
            return TxResult.FAIL;
        }

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
        if (dirtyTarget == null) {
            return TxResult.FAIL;
        }

        // Use the pipeline's own machinery so the answer matches what Mixin does at runtime.
        List<AbstractInsnNode> constantInsns = context.methods().computeInjectionTargetInsns(
            dirtyTarget,
            () -> context.methodAnnotation().getNested("constant").orElse(null),
            (ctx, handle) -> new BeforeConstant(ctx, handle.unwrap(), Type.getReturnType(context.methodNode().desc).getDescriptor()),
            false
        );

        if (constantInsns.isEmpty()) {
            return TxResult.FAIL;
        }

        return TxResult.SUCCESS;
    }
}


