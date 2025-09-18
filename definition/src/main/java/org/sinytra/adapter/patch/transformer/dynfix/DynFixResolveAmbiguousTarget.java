package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.type.MixinTypes;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

/**
 * Handle cases where a mixin with no descriptor in its target tmethod selector has come to have multiple candidate injection methods
 * as a result of Forge adding one with the same name.
 */
@Deprecated
public class DynFixResolveAmbiguousTarget implements DynamicFixer<DynFixResolveAmbiguousTarget.Data> {
    public record Data(Pair<ClassNode, List<MethodNode>> candidates) {}

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        // TEMP: Let UTP handle known types, gradually transition. // TODO
        if (MixinTypes.getMixinType(Type.getType(methodContext.methodAnnotation().getDesc()).getInternalName()) != null) {
            return null;
        }

        MethodQualifier qualifier = methodContext.getTargetMethodQualifier();
        if (qualifier != null && qualifier.isFull()) {
            return null;
        }

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
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        List<MethodNode> candidates = data.candidates().getSecond();
        for (MethodNode target : candidates) {
            if (candidates.size() == 1 || !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(data.candidates().getFirst(), target)).isEmpty()) {
                String newTarget = target.name + target.desc;
                methodContext.recordAudit(this, "Resolve ambigous method selector to %s", newTarget);
                return FixResult.of(BundledMethodTransform.builder().modifyTarget(newTarget).apply(methodContext), PatchAuditTrail.Match.FULL);
            }
        }
        return null;
    }
}
