package org.sinytra.adapter.pipeline.processor.relocate;

import org.sinytra.adapter.edit.RelocationGenerator;
import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.Processor;

public class RelocationProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        String destinationClass = dirty.getTargetClass();
        if (recipe.clean().getTargetClass().equals(destinationClass)) return TxResult.PASS;

        switch (RelocationSolver.solve(context, recipe, dirty)) {
            case RelocationSolver.Result.Solved(RelocationPlan.Move move) -> {
                RelocationGenerator.generateMove(context, destinationClass, move);
                context.recordCtxAudit("Move mixin to target %s", destinationClass);
            }
            case RelocationSolver.Result.Solved(RelocationPlan.Mirror mirror) -> {
                RelocationGenerator.generateMirror(context, destinationClass, mirror);
                context.recordCtxAudit("Mirror mixin to target %s", destinationClass);
            }
            case RelocationSolver.Result.Unbound(String reason) -> {
                context.recordCtxAudit("Can't relocate mixin to target %s: %s", destinationClass, reason);
                return TxResult.FAIL;
            }
        }
        return TxResult.SUCCESS;
    }
}
