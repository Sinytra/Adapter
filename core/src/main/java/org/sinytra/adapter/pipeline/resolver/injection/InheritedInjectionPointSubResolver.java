package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.api.BytecodeFixerUpper;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.ctx.PatchContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;

public class InheritedInjectionPointSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE)) return null;

        AtData at = recipe.clean().getAtData();
        MethodQualifier atTarget = at.getTargetQualifier().orElse(null);
        if (atTarget == null || atTarget.internalOwnerName() == null) return null;

        TargetPair targetPair = recipe.getDirtyTarget();
        if (targetPair == null) return null;
        if (context.methods().hasInjectionTargetInsns(targetPair)) return null;

        String owner = atTarget.internalOwnerName();
        for (AbstractInsnNode insn : targetPair.methodNode().instructions) {
            if (insn instanceof MethodInsnNode minsn
                && minsn.name.equals(atTarget.name()) && minsn.desc.equals(atTarget.desc()) && !minsn.owner.equals(owner)
                && (context.environment().inheritanceHandler(context.dirtyLookup()).isClassInherited(minsn.owner, owner) || isFixedField(minsn, context.patchContext()))
            ) {
                MutableConfiguration config = MutableConfiguration.create();
                config.setAtData(at.withTarget(minsn));

                if (context.hasFlag(MixinFlag.AT_TARGET_SENSITIVE) && minsn.getOpcode() != Opcodes.INVOKESTATIC) {
                    MethodParameters params = recipe.clean().getParameters().copy();
                    List<Parameter> callParams = params.get(ParamGroup.METHOD_PARAMS);
                    if (!callParams.isEmpty()) {
                        Parameter first = callParams.getFirst().extend()
                            .annotate(MixinAnnotations.COERCE, b -> b.visible(false))
                            .build();
                        callParams.set(0, first);
                        config.setParameters(params);
                    }
                }

                return config;
            }
        }

        return null;
    }

    private boolean isFixedField(AbstractInsnNode insn, PatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        return bfu != null && InsnTraversal.untilLabel(insn, InsnTraversal.BACKWARD).stream()
            .filter(FieldInsnNode.class::isInstance)
            .map(FieldInsnNode.class::cast)
            .findFirst()
            .map(finsn -> bfu.getFieldTypeChange(finsn.owner, finsn.name) != null)
            .orElse(false);
    }
}
