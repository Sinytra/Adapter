package org.sinytra.adapter.pipeline.injector;

import org.sinytra.adapter.model.RelocationPlan;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.model.param.RedirectedInsn;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolvers;

import java.util.List;

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class WrapWithConditionMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CONDITION,
        slot(METHOD_PARAMS, CALL_OPERANDS),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public WrapWithConditionMixin() {
        super(TEMPLATE, MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public RelocationPlan.Passthrough passthrough(MixinContext context, Configuration config) {
        return RelocationPlan.Passthrough.PROCEED;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        TargetPair cleanTarget = clean.getTargetMethod() != null ? context.cleanMethods().resolve(clean.getTargetMethod()) : null;
        AbstractInsnNode insn = context.injections().resolveFirst(cleanTarget);
        RedirectedInsn wrapped = insn != null ? RedirectedInsn.of(insn) : null;
        List<Parameter> params = Parameters.parse(context.methodNode());
        if (wrapped == null || wrapped.operands().size() > params.size()) {
            return TxResult.FAIL;
        }
        int operands = wrapped.operands().size();
        MethodParameters rest = MethodParameters.create(params.subList(operands, params.size()), List.of(CAPTURED_PARAMS, LOCALS));
        clean.setParameters(MethodParameters.builder()
            .put(METHOD_PARAMS, params.subList(0, operands))
            .put(CAPTURED_PARAMS, rest.get(CAPTURED_PARAMS))
            .put(LOCALS, rest.get(LOCALS))
            .putRemaining(rest)
            .build());
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null) return TxResult.FAIL;

        return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null);
    }
}
