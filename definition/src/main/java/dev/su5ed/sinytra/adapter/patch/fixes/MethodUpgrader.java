package dev.su5ed.sinytra.adapter.patch.fixes;

import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyArgsOffsetTransformer;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class MethodUpgrader {

    public static void upgradeMethod(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, String originalDesc, String modifiedDesc) {
        MethodQualifier cleanQualifier = MethodQualifier.create(originalDesc).orElse(null);
        if (cleanQualifier == null) {
            return;
        }
        MethodQualifier dirtyQualifier = MethodQualifier.create(modifiedDesc).orElse(null);
        if (dirtyQualifier == null) {
            return;
        }
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.handleModifiedDesc(methodNode, cleanQualifier.desc(), dirtyQualifier.desc());
        }
    }

}
