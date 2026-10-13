package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.LocalResolver;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.rewrite.RewriteResolver;
import org.sinytra.adapter.pipeline.resolver.rewrite.ShiftedVariableToExpression;

import java.util.List;

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.LOCALS;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.SINGLE_ANY;

public class ModifyVariableMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .keys(MixinKeys.ARGS_ONLY, MixinKeys.ORDINAL, MixinKeys.INDEX, MixinKeys.SLICE)
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.VARIABLE,
        slot(SINGLE_ANY, VALUE),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public ModifyVariableMixin() {
        super(TEMPLATE, MixinFlag.TARGETS_VARIABLE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        resolvers
            .addBefore(InjectionPointResolver.class, new RewriteResolver(List.of(new ShiftedVariableToExpression())))
            .addAfter(InjectionPointResolver.class, new LocalResolver());

        clean.setParameters(SIGNATURE.parse(context.methodNode()));

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) {
            return TxResult.FAIL;
        }
        
        dirty.inheritProperyIfAbsent(MixinKeys.SLICE);

        boolean argsOnly = clean.getProperty(MixinKeys.ARGS_ONLY).orElse(false);
        String cleanDesc = clean.getTargetMethod().desc();
        boolean targetChanged = cleanDesc != null && !dirty.getTargetMethod().desc().equals(cleanDesc);
        Type cleanVarType = clean.getParameters().getTypes(SINGLE_ANY).getFirst();
        if (argsOnly && targetChanged) {
            List<Type> cleanTargetMethodParams = Parameters.getParameterTypes(cleanDesc);
            int cleanIndex = cleanTargetMethodParams.indexOf(cleanVarType);

            List<Type> dirtyTargetMethodParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
            // The modified argument can't be located in the new target method
            if (cleanIndex == -1 || cleanIndex >= dirtyTargetMethodParams.size()) {
                return TxResult.FAIL;
            }
            Type dirtyVarType = dirtyTargetMethodParams.get(cleanIndex);

            TypeAdapter adapter = context.getTypeAdapter(cleanVarType, dirtyVarType);
            if (adapter != null) {
                return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, dirtyVarType);
            }
        }

        if (targetChanged && !clean.getParameters().getTypes(CAPTURED_PARAMS).isEmpty()) {
            return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, cleanVarType);
        }

        dirty.inheritSignature();

        return TxResult.SUCCESS;
    }

    @Override
    public boolean canInject(MixinContext context, Configuration config) {
        // Ensure variable at index has the correct type
        if (config.hasProperty(MixinKeys.INDEX)) {
            TargetPair targetPair = context.dirtyMethods().resolve(config.getTargetMethod());
            if (targetPair == null) return false;
            MethodNode target = targetPair.methodNode();

            Integer index = config.getProperty(MixinKeys.INDEX).orElseThrow();
            LocalVariableLookup lookup = LocalVariableLookup.of(target);
            if (lookup == null) return false;

            LocalVariableNode node = lookup.getByIndexOrNull(index);
            if (node == null) return false;

            Type expectedType = config.getReturnType();
            Type actualType = Type.getType(node.desc);
            return expectedType.equals(actualType);
        }
        return true;
    }
}
