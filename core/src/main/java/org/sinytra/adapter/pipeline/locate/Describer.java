package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;
import java.util.Objects;

public final class Describer {
    @Nullable
    public static Configuration describe(MixinContext context, Recipe recipe, Candidate candidate) {
        AtData cleanAt = recipe.clean().getAtData();
        if (cleanAt == null) {
            return null;
        }

        AtData at = candidate.insn() instanceof MethodInsnNode call && cleanAt.getTarget().isPresent() ? cleanAt.withTarget(call) : cleanAt;
        List<AbstractInsnNode> selected = context.injections().find(candidate.target(), InjectionQuery.of(at.withOrdinal(null)));
        int ordinal = selected.indexOf(candidate.insn());
        if (ordinal == -1) {
            return null;
        }

        AtData described = cleanAt.getOrdinal().isEmpty() ? at.withOrdinal(null) : at.withOrdinal(ordinal);
        MutableConfiguration config = MutableConfiguration.create().setAtData(described);

        if (candidate.target() != recipe.getDirtyTarget()) {
            config.setTargetMethod(candidate.target().methodNode());
            String targetClass = candidate.target().classNode().name;
            if (!targetClass.equals(recipe.dirty().getTargetClass())) {
                config.setTargetClass(targetClass);
                if (candidate.relocation() != null) {
                    config.setProperty(IntentKeys.RELOCATION, candidate.relocation());
                }
            }
        }
        return config;
    }

    /// {@return whether both configurations describe the same injection point}
    public static boolean isSame(Configuration a, Configuration b) {
        return Objects.equals(a.getAtData(), b.getAtData())
            && Objects.equals(a.getTargetMethod(), b.getTargetMethod())
            && Objects.equals(a.getTargetClass(), b.getTargetClass());
    }

    private Describer() {
    }
}
