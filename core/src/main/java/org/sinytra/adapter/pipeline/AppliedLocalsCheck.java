package org.sinytra.adapter.pipeline;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.locate.LocalLocator;
import org.sinytra.adapter.pipeline.processor.SugarLocalProcessor;
import org.sinytra.adapter.util.MixinBridge;

public final class AppliedLocalsCheck {
    public static void check(Object actor, MixinContext context, TargetPair cleanTarget, TargetPair dirtyTarget) {
        AbstractInsnNode cleanInsn = context.injections().resolveFirst(cleanTarget);
        AbstractInsnNode dirtyInsn = context.injections().resolveFirst(dirtyTarget);
        if (cleanInsn == null || dirtyInsn == null) {
            return;
        }

        MethodNode handler = context.methodNode();
        if (context.hasFlag(MixinFlag.TARGETS_VARIABLE)) {
            Type type = Type.getReturnType(handler.desc);
            int cleanSlot = context.locals().resolveSelectedLocal(cleanTarget, cleanInsn, type);
            int slot = context.locals().resolveSelectedLocal(dirtyTarget, dirtyInsn, type);
            report(actor, context, "The variable selector", cleanTarget, cleanSlot, cleanInsn, dirtyTarget, slot);
        }

        Type[] params = Type.getArgumentTypes(handler.desc);
        MixinBridge.Session cleanSession = null;
        MixinBridge.Session dirtySession = null;
        for (int i = 0; i < params.length; i++) {
            AnnotationNode local = SugarLocalProcessor.getLocalAnnotation(handler, i);
            if (local == null) {
                continue;
            }
            if (cleanSession == null) {
                cleanSession = MixinBridge.session(context.classNode().name, handler, cleanTarget.classNode(), cleanTarget.methodNode(), context.environment());
                dirtySession = MixinBridge.session(context.classNode().name, handler, dirtyTarget.classNode(), dirtyTarget.methodNode(), context.environment());
            }
            int cleanSlot = cleanSession.findLocal(local, params[i], cleanInsn);
            int slot = dirtySession.findLocal(local, params[i], dirtyInsn);
            report(actor, context, "@Local parameter " + i, cleanTarget, cleanSlot, cleanInsn, dirtyTarget, slot);
        }
    }

    private static void report(Object actor, MixinContext context, String selector, TargetPair cleanTarget, int cleanSlot, AbstractInsnNode cleanInsn,
                               TargetPair dirtyTarget, int slot) {
        if (cleanSlot == -1 || slot == -1) {
            return;
        }
        int expected = LocalLocator.resolve(context, cleanTarget, dirtyTarget, cleanSlot, cleanInsn, dirtyTarget);
        if (expected != -1 && expected != slot) {
            context.recordAudit(actor, "Warning: %s selects local %s, but local %s may now hold what clean local %s held", selector, slot, expected, cleanSlot);
        }
    }

    private AppliedLocalsCheck() {
    }
}
