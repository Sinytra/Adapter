package org.sinytra.adapter.pipeline.preprocess;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariable;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.api.AuditTrail;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.mixin.MixinBridge.LocalCandidate;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.*;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_ORDINAL;

public class LocalCaptureUpgradeTransformer implements MethodTransformer {
    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        // Check requirements
        Targets targets = context.targets(config.getTargetMethod());
        if (targets == null) return PatchResult.PASS;

        if (!config.hasProperty(MixinKeys.LOCALS)) return PatchResult.PASS;

        // Analyze locals
        MethodNode methodNode = context.methodNode();
        // Already upgraded
        if (Parameters.parse(methodNode).stream().anyMatch(p -> p.hasAnnotation(MixinAnnotations.LOCAL))) return PatchResult.PASS;

        LocalsAnalyzer.CapturedLocalsInfo info = context.locals().resolveCapturedLocalsInfo(targets.dirty());
        if (info == null || info.diff().isEmpty()) return PatchResult.PASS;

        LocalsAnalyzer.CapturedLocalsTransform transform = LocalsAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);

        List<AbstractInsnNode> cleanInsns = context.injections().find(targets.clean());
        List<AbstractInsnNode> dirtyInsns = context.injections().find(targets.dirty());
        List<LocalVariable> cleanAvailable = context.locals().resolveAvailableLocals(targets.clean());
        if (cleanInsns.isEmpty() || dirtyInsns.isEmpty() || cleanAvailable == null) return PatchResult.PASS;

        LocalVariableLookup lookup = info.capturedLocals().lvt();
        int capturedStart = info.capturedLocals().paramLocalStart();
        Type[] args = Type.getArgumentTypes(methodNode.desc);
        Map<Integer, Integer> parameterToOrdinal = new HashMap<>();
        Set<Integer> usedOrdinals = new HashSet<>();
        for (LocalVariableNode node : transform.usedLocalNodes()) {
            Type expected = Type.getType(node.desc);
            boolean addOrdinal = true;

            int paramOrdinal = lookup.getParameterOrdinal(node);
            if (paramOrdinal < capturedStart) return PatchResult.PASS;
            long typedPosition = Arrays.stream(args, capturedStart, paramOrdinal).filter(expected::equals).count();
            LocalVariable cleanLocal = cleanAvailable.stream()
                .filter(l -> l.type().equals(expected))
                .skip(typedPosition)
                .findFirst()
                .orElse(null);
            if (cleanLocal == null) return PatchResult.PASS;

            List<LocalCandidate> cleanLocals = context.locals().findSelectableLocals(targets.clean(), cleanInsns.getFirst(), expected);
            List<LocalCandidate> dirtyLocals = context.locals().findSelectableLocals(targets.dirty(), dirtyInsns.getFirst(), expected);
            if (cleanLocals.size() != dirtyLocals.size()) {
                long matching = info.availableTypes().stream()
                    .filter(expected::equals)
                    .count();
                if (matching != 1)
                    return PatchResult.PASS;

                addOrdinal = false;
            }

            int localOrdinal = cleanLocals.stream()
                .filter(c -> c.index() == cleanLocal.index())
                .mapToInt(LocalCandidate::ordinal)
                .findFirst()
                .orElse(-1);
            if (localOrdinal == -1) return PatchResult.PASS;

            usedOrdinals.add(paramOrdinal);

            if (addOrdinal && cleanLocals.size() > 1) {
                parameterToOrdinal.put(paramOrdinal, localOrdinal);
            }
        }

        int start = info.capturedLocals().paramLocalStart();
        for (int i = start; i < args.length; i++) {
            if (!usedOrdinals.contains(i)) continue;

            AnnotationVisitor visitor = methodNode.visitParameterAnnotation(i, MixinAnnotations.LOCAL, false);
            if (parameterToOrdinal.containsKey(i)) {
                visitor.visit(PROPERTY_ORDINAL, parameterToOrdinal.get(i));
            }
        }

        PatchResult result = transform.remover().isEmpty() ? PatchResult.APPLY : transform.remover().apply(context);
        if (result == PatchResult.PASS) return PatchResult.PASS;

        context.recordCtxAudit("Upgrade captured locals");
        context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
        return result;
    }
}
