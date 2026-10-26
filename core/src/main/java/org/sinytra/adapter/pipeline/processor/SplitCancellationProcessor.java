package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.pipeline.processor.relocate.RelocationGenerator;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;
import java.util.stream.IntStream;

public class SplitCancellationProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (!recipe.clean().isCancellable()) {
            return TxResult.PASS;
        }

        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair split = recipe.getNewCleanTarget();
        TargetPair piece = recipe.getDirtyTarget();
        if (cleanTarget == null || split == null || piece == null
            || !MethodAnalyzer.isDirtyDeprecatedMethod(cleanTarget.methodNode(), split.methodNode())
            || Type.getReturnType(split.methodNode().desc) != Type.VOID_TYPE
        ) {
            return TxResult.PASS;
        }

        List<MethodNode> pieces = context.dataFlow(split).findTopLevelOwnCalls();
        MethodNode target = piece.methodNode();
        int index = IntStream.range(0, pieces.size())
            .filter(i -> pieces.get(i).name.equals(target.name) && pieces.get(i).desc.equals(target.desc))
            .findFirst()
            .orElse(-1);
        List<MethodNode> laterPieces = index != -1 ? pieces.subList(index + 1, pieces.size()) : List.of();
        if (laterPieces.isEmpty()) {
            return TxResult.PASS;
        }

        if (laterPieces.stream().anyMatch(m -> MethodAnalyzer.isStatic(m) != context.isStatic())) {
            context.recordCtxAudit("Can't propagate cancellation: the pieces of %s differ in static-ness", split.methodNode().name);
            return TxResult.PASS;
        }

        RelocationGenerator.generateCancellers(context, split.classNode(), laterPieces);
        context.recordCtxAudit("Propagate cancellation to %s", laterPieces.stream().map(m -> m.name).toList());
        return TxResult.SUCCESS;
    }
}
