package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;

public interface Processor {
    TxResult process(MixinContext context, Configuration dirty, Recipe recipe);
}
