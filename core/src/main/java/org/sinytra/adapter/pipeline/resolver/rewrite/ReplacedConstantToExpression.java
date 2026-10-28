package org.sinytra.adapter.pipeline.resolver.rewrite;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.ann.ConstantData;
import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinTypes;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_FIELD;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;

public final class ReplacedConstantToExpression implements RewriteRule {
    @Nullable
    @Override
    public Rewrite apply(MixinContext context, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();

        ConstantData constant = recipe.clean().getProperty(MixinKeys.TARGET_CONSTANT).orElse(null);
        if (cleanTarget == null || dirtyTarget == null || constant == null || constant.getProperty(ConstantData.Keys.SLICE).isPresent()
            || recipe.clean().getProperty(MixinKeys.SLICES).map(slices -> !slices.isEmpty()).orElse(false)
        ) {
            return null;
        }

        List<AbstractInsnNode> constants = context.injections().find(cleanTarget);
        if (constants.size() != 1) {
            return null;
        }

        AbstractInsnNode cleanConstant = constants.getFirst();
        AbstractInsnNode cleanConsumer = nextReal(cleanConstant);
        MethodAlignment alignment = MethodAlignment.align(cleanTarget.methodNode(), dirtyTarget.methodNode());
        if (cleanConsumer == null || alignment.resolve(cleanConstant) != null) {
            return null;
        }

        Type type = Type.getReturnType(context.methodNode().desc);
        List<AbstractInsnNode> replacements = alignment.findDirtyAround(cleanConstant).stream()
            .filter(insn -> type.equals(getProducedType(insn)))
            .filter(insn -> nextReal(insn) != null && nextReal(insn).getOpcode() == cleanConsumer.getOpcode())
            .toList();
        if (replacements.size() != 1) {
            return null;
        }

        AbstractInsnNode replacement = replacements.getFirst();
        AtData at = replacement instanceof FieldInsnNode field
            ? AtData.create(AT_VAL_FIELD, Type.getObjectType(field.owner).getDescriptor() + field.name + ":" + field.desc)
            : AtData.create(AT_VAL_INVOKE, (MethodInsnNode) replacement);
        List<AbstractInsnNode> selected = context.injections().find(dirtyTarget, InjectionQuery.of(at));
        int ordinal = selected.indexOf(replacement);
        if (ordinal == -1) {
            return null;
        }

        return new Rewrite.Replace(
            Configurations.changeMixinType(
                recipe.dirty(), MixinAnnotations.MODIFY_EXPR_VAL,
                MixinTypes.MODIFY_EXPR_VAL.getConfigurationTemplate(),
                selected.size() > 1 ? at.withOrdinal(ordinal) : at
            )
        );
    }

    @Nullable
    private static Type getProducedType(AbstractInsnNode insn) {
        if (insn instanceof FieldInsnNode field && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC)) {
            return Type.getType(field.desc);
        }
        if (insn instanceof MethodInsnNode call && !call.name.equals("<init>")) {
            return Type.getReturnType(call.desc);
        }
        return null;
    }

    @Nullable
    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        return InsnTraversal.find(insn, InsnTraversal.FORWARD, i -> i.getOpcode() >= 0);
    }
}
