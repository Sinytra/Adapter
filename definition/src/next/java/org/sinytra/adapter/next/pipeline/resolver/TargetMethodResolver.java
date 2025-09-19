package org.sinytra.adapter.next.pipeline.resolver;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

public class TargetMethodResolver implements Resolver {
    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        // Conditions
        if (dirty.getTargetMethod() != null) return TxResult.PASS;

        // Reuse attempt
        MethodQualifier cleanQualifier = clean.getTargetMethod();
        MethodContext.TargetPair target = context.methods().findMethod(context.dirtyLookup(), cleanQualifier);
        if (target != null) {
            dirty.inheritTargetMethod();
            return TxResult.SUCCESS;
        }

        // Find replacement
        if (handleChangedMethodParams(context, cleanQualifier, dirty))
            return TxResult.SUCCESS;

        return TxResult.FAIL;
    }

    /**
     * Handle cases where the target method's parameters have changed
     * <p>
     * For example:
     * CLEAN: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V</code>
     * <br>
     * DIRTY: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V</code>
     */
    public boolean handleChangedMethodParams(MixinContext context, MethodQualifier cleanQualifier, MutableConfiguration dirty) {
        Pair<ClassNode, List<MethodNode>> candidates = context.methods().findMethodsIgnoringDesc(context.dirtyLookup(), cleanQualifier);
        if (candidates == null) return false;

        // Find single matching candidate
        MethodNode resolved = resolveReplacementCandidate(context, candidates.getFirst(), candidates.getSecond());
        if (resolved == null) return false;

        // Only apply single candidate change when the target desc has changed
        MethodContext.TargetPair cleanTarget = context.methods().findMethod(context.cleanLookup(), cleanQualifier);
        if (!resolved.desc.equals(cleanTarget.methodNode().desc)) {
            dirty.setTargetMethod(resolved);
            return true;
        }

        return false;
    }

    @Nullable
    private MethodNode resolveReplacementCandidate(MixinContext context, ClassNode classNode, List<MethodNode> methods) {
        if (methods.size() == 1) {
            return methods.getFirst();
        }

        List<MethodNode> valid = new ArrayList<>();
        for (MethodNode method : methods) {
            if (!context.methods().findInjectionTargetInsns(new MethodContext.TargetPair(classNode, method)).isEmpty()) {
                valid.add(method);
            }
        }

        return valid.size() == 1 ? valid.getFirst() : null;
    }
}
