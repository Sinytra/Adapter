package org.sinytra.adapter.pipeline.processor.signature;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.HandlerSignature.ParamSource;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processor;

import java.util.ArrayList;
import java.util.List;

public class SignatureProcessor implements Processor {
    private static final SignatureStep PARAMETERS = new ParametersStep();
    private static final SignatureStep OPERATION_CALLS = new OperationCallStep();
    private static final SignatureStep REDIRECTED_CALLS = new RedirectedCallStep();
    private static final SignatureStep CASTS = new ParameterCastStep();
    private static final SignatureStep RETURN_TYPE = new ReturnTypeStep();
    private static final SignatureStep STATIC_ACCESS = new StaticAccessStep();

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        TxResult result = TxResult.PASS;
        for (SignatureStep step : steps(context.getMixinType().signature())) {
            TxResult stepResult = context.audited(step, () -> step.apply(context, dirty, recipe));
            if (stepResult == TxResult.FAIL || stepResult == TxResult.FINALIZE) {
                return stepResult;
            }
            if (stepResult == TxResult.SUCCESS) {
                result = TxResult.SUCCESS;
            }
        }
        return result;
    }

    private List<SignatureStep> steps(HandlerSignature signature) {
        List<SignatureStep> steps = new ArrayList<>();
        steps.add(PARAMETERS);
        if (hasSource(signature, ParamSource.CALL_OPERANDS)) {
            steps.add(hasSource(signature, ParamSource.OPERATION) ? OPERATION_CALLS : REDIRECTED_CALLS);
        }
        steps.add(CASTS);
        steps.add(RETURN_TYPE);
        steps.add(STATIC_ACCESS);
        return steps;
    }

    private static boolean hasSource(HandlerSignature signature, ParamSource source) {
        return signature.slots().stream().anyMatch(slot -> slot.source() == source);
    }
}
