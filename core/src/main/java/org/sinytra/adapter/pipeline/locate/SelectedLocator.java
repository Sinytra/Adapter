package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public final class SelectedLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Scope scope, AbstractInsnNode cleanInsn) {
        AtData cleanAt = recipe.clean().getAtData();
        if (cleanAt == null || scope.kind() == Scope.Kind.SAME) {
            return List.of();
        }
        return Describer.findSelectable(context, scope.method(), cleanAt, at -> InjectionQuery.of(at).ignoringSlice()).stream()
            .map(insn -> scope.candidate(insn, MethodAlignment.Confidence.POSITIONAL))
            .toList();
    }
}
