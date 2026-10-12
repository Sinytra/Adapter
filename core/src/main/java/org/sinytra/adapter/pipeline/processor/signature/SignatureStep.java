package org.sinytra.adapter.pipeline.processor.signature;

import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public interface SignatureStep {
    TxResult apply(MixinContext context, Configuration dirty, Recipe recipe);
}
