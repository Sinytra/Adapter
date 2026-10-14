package org.sinytra.adapter.pipeline.resolver;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocalLocator;

public final class LocalResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        Configuration dirty = recipe.dirty();
        AtData at = dirty.getAtData();
        if (at == null || !recipe.clean().hasProperty(MixinKeys.ORDINAL) && !recipe.clean().hasProperty(MixinKeys.INDEX)
            || dirty.hasProperty(MixinKeys.ORDINAL) || dirty.hasProperty(MixinKeys.INDEX)
        ) {
            return ResolutionResult.pass();
        }

        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (cleanTarget == null || dirtyTarget == null) {
            return ResolutionResult.pass();
        }
        AbstractInsnNode cleanInsn = context.injections().resolveFirst(cleanTarget);
        AbstractInsnNode dirtyInsn = context.injections().resolveFirst(dirtyTarget, InjectionQuery.of(at));
        if (cleanInsn == null || dirtyInsn == null) {
            return ResolutionResult.pass();
        }

        Type type = Type.getReturnType(context.methodNode().desc);
        int cleanSlot = context.locals().resolveSelectedLocal(cleanTarget, cleanInsn, type);
        if (cleanSlot < 0) {
            return ResolutionResult.pass();
        }
        int dirtySlot = LocalLocator.resolve(context, recipe, cleanTarget, cleanSlot, cleanInsn, dirtyTarget);
        if (dirtySlot < 0 || context.locals().resolveSelectedLocal(dirtyTarget, dirtyInsn, type) == dirtySlot) {
            return ResolutionResult.pass();
        }

        MutableConfiguration local = Describer.describeLocal(context, recipe, dirtyTarget, dirtyInsn, dirtySlot);
        if (local == null) {
            return ResolutionResult.pass();
        }
        context.recordCtxAudit("Select local %s instead of %s", dirtySlot, cleanSlot);
        return ResolutionResult.success(local);
    }
}
