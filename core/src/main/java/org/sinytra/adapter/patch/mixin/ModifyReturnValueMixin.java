package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ctx.MixinContext;
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
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.InjectorOrdinalResolver;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.env.ctx.Targets;

import java.util.List;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.*;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_RETURN;

public class ModifyReturnValueMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    public ModifyReturnValueMixin() {
        super(TEMPLATE);
    }

    @Override
    public boolean canInject(MixinContext context, Configuration config) {
        AtData at = config.getAtData();
        MethodQualifier target = config.getTargetMethod();
        if (at == null || !AT_VAL_RETURN.equals(at.getValue()) || at.getProperty(AtData.Keys.ORDINAL).isEmpty() || target == null) {
            return true;
        }

        Targets targets = context.methods().findTargets(target);
        return targets == null || MethodAnalyzer.findReturnInsns(targets.clean().methodNode()).size() == MethodAnalyzer.findReturnInsns(targets.dirty().methodNode()).size();
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers
            .addBefore(InjectionPointResolver.class, new InjectorOrdinalResolver());

        clean.setParameters(MethodParameters.create(context.methodNode(), List.of(SINGLE_ANY, CAPTURED_PARAMS, LOCALS)));

        return TxResult.SUCCESS;
    }

    // TODO Cleanup parameter processing in mixin types
    // 1. Method params vs Captured params + reconstruction
    // 2. Locals
    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        MethodQualifier target = dirty.getTargetMethod();
        if (target == null || target.desc() == null) {
            return TxResult.FAIL;
        }

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());
        Type modifyingType = Type.getReturnType(target.desc());

        MethodParameters params = clean.getParameters().mutableCopy()
            .putType(SINGLE_ANY, modifyingType)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(modifyingType);

        return TxResult.SUCCESS;
    }
}
