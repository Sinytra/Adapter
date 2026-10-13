package org.sinytra.adapter.pipeline.resolver.rewrite;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TypeInsnNode;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_SINYTRA_INSTANCEOF;

public final class InstanceofToModifyInstanceofValue implements RewriteRule {
    @Nullable
    @Override
    public Rewrite apply(MixinContext context, Recipe recipe) {
        if (!Type.getReturnType(context.methodNode().desc).equals(Type.BOOLEAN_TYPE)) {
            return null;
        }
        TypeInsnNode instanceOf = InstanceofReplacement.find(context, recipe);
        if (instanceOf == null) {
            return null;
        }

        List<TypeInsnNode> checks = InstanceofReplacement.findChecks(recipe.getDirtyTarget().methodNode(), instanceOf.desc);
        AtData at = AtData.builder(AT_VAL_SINYTRA_INSTANCEOF)
            .target(instanceOf.desc)
            .ordinal(checks.size() == 1 ? null : checks.indexOf(instanceOf))
            .build();
        return new Rewrite.Replace(Configurations.changeMixinType(recipe.dirty(), MixinAnnotations.MODIFY_INSTANCEOF_VAL, at));
    }
}
