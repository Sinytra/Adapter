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
import java.util.function.Function;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;

public final class Describer {
    @Nullable
    public static Configuration describe(MixinContext context, Recipe recipe, Candidate candidate) {
        AtData cleanAt = recipe.clean().getAtData();
        AtData described = cleanAt != null ? describeAt(context, cleanAt, candidate, InjectionQuery::of) : null;
        if (described == null) {
            return null;
        }
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

    @Nullable
    public static AtData describeAt(MixinContext context, AtData cleanAt, Candidate candidate, Function<AtData, InjectionQuery> query) {
        boolean changesKind = candidate.insn() instanceof MethodInsnNode && !AT_VAL_INVOKE.equals(cleanAt.getValue()) && !AT_VAL_INVOKE_ASSIGN.equals(cleanAt.getValue());
        AtData at;
        if (changesKind) {
            at = AtData.create(AT_VAL_INVOKE, (MethodInsnNode) candidate.insn());
        } else {
            at = candidate.insn() instanceof MethodInsnNode call && cleanAt.getTarget().isPresent() ? cleanAt.withTarget(call) : cleanAt;
        }
        List<AbstractInsnNode> selected = context.injections().find(candidate.target(), query.apply(at.withOrdinal(null)));
        int ordinal = selected.indexOf(candidate.insn());
        if (ordinal == -1) {
            return null;
        }
        boolean keepsOrdinal = changesKind ? selected.size() > 1 : cleanAt.getOrdinal().isPresent();
        return keepsOrdinal ? at.withOrdinal(ordinal) : at.withOrdinal(null);
    }

    public static boolean isSame(Configuration a, Configuration b) {
        return Objects.equals(a.getAtData(), b.getAtData())
            && Objects.equals(a.getTargetMethod(), b.getTargetMethod())
            && Objects.equals(a.getTargetClass(), b.getTargetClass());
    }

    private Describer() {
    }
}
