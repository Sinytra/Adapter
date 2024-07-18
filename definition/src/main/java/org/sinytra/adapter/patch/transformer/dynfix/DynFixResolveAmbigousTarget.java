package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

/**
 * Handle cases where a mixin with no descriptor in its target tmethod selector has come to have multiple candidate injection methods
 * as a result of Forge adding one with the same name.
 */
public class DynFixResolveAmbigousTarget implements DynamicFixer<DynFixResolveAmbigousTarget.Data> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Data(Pair<ClassNode, List<MethodNode>> candidates) {
    }

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        Pair<ClassNode, List<MethodNode>> candidates = methodContext.findInjectionTargetCandidates(methodContext.patchContext().environment().dirtyClassLookup(), true);
        if (candidates != null && !candidates.getSecond().isEmpty()) {
            // Only apply single candidate change when the target desc has changed
            if (candidates.getSecond().size() == 1) {
                MethodContext.TargetPair cleanTarget = methodContext.findCleanInjectionTarget();
                if (cleanTarget == null || candidates.getSecond().getFirst().desc.equals(cleanTarget.methodNode().desc)) {
                    return null;
                }
            }
            return new Data(candidates);
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        List<MethodNode> candidates = data.candidates().getSecond();
        for (MethodNode target : candidates) {
            if (candidates.size() == 1 || !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(data.candidates().getFirst(), target)).isEmpty()) {
                String newTarget = target.name + target.desc;
                LOGGER.debug(MIXINPATCH, "Resolving ambigous method selector of {}.{} to {}", classNode.name, methodNode.name, newTarget);
                return BundledMethodTransform.builder().modifyTarget(newTarget).apply(methodContext);
            }
        }
        return Patch.Result.PASS;
    }
}
