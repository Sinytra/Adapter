package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public final class Describer {
    @Nullable
    public static Configuration describe(MixinContext context, Recipe recipe, Candidate candidate) {
        AtData cleanAt = recipe.clean().getAtData();
        if (cleanAt == null || candidate.target() != recipe.getDirtyTarget()) {
            return null;
        }

        AtData at = candidate.insn() instanceof MethodInsnNode call && cleanAt.getTarget().isPresent() ? cleanAt.withTarget(call) : cleanAt;
        List<AbstractInsnNode> selected = context.injections().find(candidate.target(), InjectionQuery.of(at.withOrdinal(null)));
        int ordinal = selected.indexOf(candidate.insn());
        if (ordinal == -1) {
            return null;
        }

        AtData described = selected.size() == 1 || cleanAt.getOrdinal().isEmpty() ? at.withOrdinal(null) : at.withOrdinal(ordinal);
        return MutableConfiguration.create().setAtData(described);
    }

    private Describer() {
    }
}
