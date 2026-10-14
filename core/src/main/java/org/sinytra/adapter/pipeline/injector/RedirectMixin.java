package org.sinytra.adapter.pipeline.injector;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.ConfigurationTemplates;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotationConstants;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ValueSource;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.model.param.RedirectedInsn;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.InjectionPointResolver;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.pipeline.resolver.rewrite.InstanceofMakesRedirectRedundant;
import org.sinytra.adapter.pipeline.resolver.rewrite.RewriteResolver;

import java.util.List;

import static org.sinytra.adapter.model.param.HandlerSignature.ParamSource.*;
import static org.sinytra.adapter.model.param.HandlerSignature.slot;
import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.*;

public class RedirectMixin extends AbstractMixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    private static final HandlerSignature SIGNATURE = HandlerSignature.of(
        ValueSource.CALL_RETURN,
        slot(METHOD_PARAMS, CALL_OPERANDS),
        slot(CAPTURED_PARAMS, CAPTURED_TARGET_ARGS),
        slot(LOCALS, KEEP)
    );

    public RedirectMixin() {
        super(TEMPLATE, MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public HandlerSignature signature() {
        return SIGNATURE;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers) {
        resolvers.
            addBefore(InjectionPointResolver.class, new RewriteResolver(List.of(new InstanceofMakesRedirectRedundant())));

        if (clean.getAtData() == null)
            return TxResult.FAIL;

        List<Type> callTypes;
        if (MixinAnnotationConstants.AT_VAL_INVOKE.equals(clean.getAtData().getValue())) {
            MethodQualifier targetDesc = clean.getAtData().getTargetQualifier().orElse(null);
            if (targetDesc == null || targetDesc.desc() == null)
                return TxResult.FAIL;

            List<Type> methodParams = Parameters.getParameterTypes(context.methodNode().desc);
            boolean isStatic = isStaticRedirect(context, targetDesc, methodParams, Parameters.getParameterTypes(targetDesc.desc()));
            if (!isStatic && methodParams.isEmpty())
                return TxResult.FAIL;
            callTypes = Parameters.getCallOperandTypes(isStatic ? null : methodParams.getFirst(), targetDesc.desc());
        } else {
            TargetPair cleanTarget = clean.getTargetMethod() != null ? context.cleanMethods().resolve(clean.getTargetMethod()) : null;
            AbstractInsnNode insn = context.injections().resolveFirst(cleanTarget);
            RedirectedInsn redirected = insn != null ? RedirectedInsn.of(insn) : null;
            if (redirected == null)
                return TxResult.FAIL;
            callTypes = redirected.operands();
        }

        MethodParameters capturedMethodParams = MethodParameters.create(
            Parameters.parse(context.methodNode(), callTypes.size(), -1),
            List.of(ParamGroup.CAPTURED_PARAMS, ParamGroup.LOCALS)
        );

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, capturedMethodParams.getTypes(CAPTURED_PARAMS))
            .putTypes(LOCALS, capturedMethodParams.getTypes(LOCALS))
            .putRemaining(capturedMethodParams)
            .build();

        clean.setParameters(params);

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null || dirty.getAtData() == null)
            return TxResult.FAIL;

        return HandlerSignatures.apply(context, SIGNATURE, clean, dirty, recipe, null);
    }

    private boolean isStaticRedirect(MixinContext context, MethodQualifier targetDesc, List<Type> methodParams, List<Type> callTypes) {
        TargetPair cleanTarget = context.cleanMethods().resolve(targetDesc);
        if (cleanTarget != null) {
            return MethodAnalyzer.isStatic(cleanTarget.methodNode());
        }
        return methodParams.size() >= callTypes.size() && methodParams.subList(0, callTypes.size()).equals(callTypes);
    }
}
