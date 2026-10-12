package org.sinytra.adapter.pipeline.resolver.special;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.locals.LocalAlignment;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.resolver.Resolver;
import org.sinytra.adapter.util.AdapterUtil;

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
        AbstractInsnNode cleanInsn = AdapterUtil.getFirstOrNull(context.injections().find(cleanTarget));
        AbstractInsnNode dirtyInsn = AdapterUtil.getFirstOrNull(context.injections().find(dirtyTarget, InjectionQuery.of(at)));
        if (cleanInsn == null || dirtyInsn == null) {
            return ResolutionResult.pass();
        }

        Type type = Type.getReturnType(context.methodNode().desc);
        int cleanSlot = context.locals().resolveSelectedLocal(cleanTarget, cleanInsn, type);
        if (cleanSlot < 0) {
            return ResolutionResult.pass();
        }
        int dirtySlot = findDirtySlot(recipe, cleanTarget, dirtyTarget, cleanSlot, cleanInsn);
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

    private static int findDirtySlot(Recipe recipe, TargetPair cleanTarget, TargetPair dirtyTarget, int cleanSlot, AbstractInsnNode cleanInsn) {
        if (cleanTarget.methodNode().name.equals(dirtyTarget.methodNode().name) && cleanTarget.methodNode().desc.equals(dirtyTarget.methodNode().desc)) {
            MethodAlignment alignment = MethodAlignment.align(cleanTarget.methodNode(), dirtyTarget.methodNode());
            return LocalAlignment.of(alignment).resolve(cleanSlot, cleanInsn);
        }

        LocalVariableLookup cleanLocals = recipe.cleanLocalsTable();
        LocalVariableNode cleanLocal = cleanLocals != null ? cleanLocals.getInScopeOrNull(cleanSlot, cleanInsn) : null;
        if (cleanLocal == null) {
            return -1;
        }

        InsnList initializer = LocalsAnalyzer.findInitializerInsns(cleanTarget.methodNode(), cleanSlot);
        return LocalsAnalyzer.findLocalWithInitializer(dirtyTarget.methodNode(), cleanLocal.desc, initializer)
            .map(local -> local.index)
            .orElse(-1);
    }
}
