package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.METHOD_PARAMS;

/**
 * Handles {@code @Overwrite} mixins, the one handler that carries no target information at all: it replaces
 * the target method wholesale and is identified by the handler's own name, which {@link MixinType#deriveTarget}
 * supplies (name only) so the usual name resolution can complete it.
 */
public class OverwriteMixin implements MixinType {
    /** TARGET_METHOD is not required here: {@code @Overwrite} derives it from the handler. */
    private static final PropertyContainerTemplate TEMPLATE = PropertyContainerTemplate.builder()
        .require(ControlKeys.MIXIN_TYPE, ControlKeys.TARGET_CLASS, ControlKeys.PARAMETERS, ControlKeys.RETURN_TYPE)
        .keys(MixinKeys.LOCALS, MixinKeys.REQUIRE)
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public MethodQualifier deriveTarget(PropertyContainer properties, MethodNode method) {
        return new MethodQualifier(method.name, null);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        // An overwrite takes over the whole method, so the handler mirrors the target's parameters.
        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(METHOD_PARAMS)));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) {
            return TxResult.FAIL;
        }

        TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
        if (target == null) {
            return TxResult.FAIL;
        }

        return TxResult.SUCCESS;
    }
}
