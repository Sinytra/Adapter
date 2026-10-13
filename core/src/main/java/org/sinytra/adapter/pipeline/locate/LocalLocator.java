package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.locals.LocalAlignment;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

public final class LocalLocator {
    public static int resolve(MixinContext context, Recipe recipe, TargetPair cleanTarget, int cleanSlot, AbstractInsnNode cleanInsn, TargetPair dirtyTarget) {
        return resolve(context, cleanTarget, recipe.getNewCleanTarget(), cleanSlot, cleanInsn, dirtyTarget);
    }

    public static int resolve(MixinContext context, TargetPair cleanTarget, @Nullable TargetPair origin, int cleanSlot, AbstractInsnNode cleanInsn, TargetPair dirtyTarget) {
        int originSlot = origin != null
            ? LocalAlignment.of(MethodAlignment.align(cleanTarget.methodNode(), origin.methodNode())).resolve(cleanSlot, cleanInsn)
            : -1;
        if (originSlot == -1 && origin != null && origin.methodNode().desc.equals(cleanTarget.methodNode().desc)
            && Describer.isParameter(cleanTarget.methodNode(), cleanSlot)) {
            originSlot = cleanSlot;
        }
        if (origin != null && isSame(origin, dirtyTarget)) {
            return originSlot;
        }
        if (originSlot != -1) {
            int passed = PassedLocals.resolve(context, origin, originSlot, dirtyTarget);
            if (passed != -1) {
                return passed;
            }
        }

        LocalVariableLookup cleanLocals = LocalVariableLookup.of(cleanTarget.methodNode());
        LocalVariableNode cleanLocal = cleanLocals != null ? cleanLocals.getInScopeOrNull(cleanSlot, cleanInsn) : null;
        if (cleanLocal == null) {
            return -1;
        }
        InsnList initializer = LocalsAnalyzer.findInitializerInsns(cleanTarget.methodNode(), cleanSlot);
        return LocalsAnalyzer.findLocalWithInitializer(dirtyTarget.methodNode(), cleanLocal.desc, initializer)
            .map(local -> local.index)
            .orElse(-1);
    }

    private static boolean isSame(TargetPair a, TargetPair b) {
        return a.classNode().name.equals(b.classNode().name) && a.methodNode().name.equals(b.methodNode().name)
            && a.methodNode().desc.equals(b.methodNode().desc);
    }

    private LocalLocator() {
    }
}
