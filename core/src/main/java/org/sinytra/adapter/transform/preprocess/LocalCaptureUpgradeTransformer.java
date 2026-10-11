package org.sinytra.adapter.transform.preprocess;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.ctx.Targets;
import org.sinytra.adapter.env.param.Parameters;

import java.util.*;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_ORDINAL;

public class LocalCaptureUpgradeTransformer implements MethodTransformer {
    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        // Check requirements
        Targets targets = context.methods().findTargets(config.getTargetMethod());
        if (targets == null) return PatchResult.PASS;

        if (!config.hasProperty(MixinKeys.LOCALS)) return PatchResult.PASS;

        // Analyze locals
        MethodNode methodNode = context.methodNode();
        // Already upgraded
        if (Parameters.parse(methodNode).stream().anyMatch(p -> p.hasAnnotation(MixinAnnotations.LOCAL))) return PatchResult.PASS;

        LocalVarAnalyzer.CapturedLocalsInfo info = LocalVarAnalyzer.getCapturedLocals(context, targets.dirty());
        if (info == null || info.diff().isEmpty()) return PatchResult.PASS;

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);

        LocalVariableLookup cleanLookup = context.methods().getLVT(targets.clean().methodNode());
        LocalVariableLookup dirtyLookup = context.methods().getLVT(targets.dirty().methodNode());
        LocalVariableLookup lookup = info.capturedLocals().lvt();
        if (cleanLookup == null || dirtyLookup == null) return PatchResult.PASS;

        Map<Integer, Integer> parameterToOrdinal = new HashMap<>();
        Set<Integer> usedOrdinals = new HashSet<>();
        for (LocalVariableNode node : transform.usedLocalNodes()) {
            Type expected = Type.getType(node.desc);
            boolean addOrdinal = true;

            List<LocalVariableNode> cleanLocals = cleanLookup.getForType(expected);
            List<LocalVariableNode> dirtyLocals = dirtyLookup.getForType(expected);
            if (cleanLocals.size() != dirtyLocals.size()) {
                long matching = info.availableTypes().stream()
                    .filter(expected::equals)
                    .count();
                if (matching != 1)
                    return PatchResult.PASS;

                addOrdinal = false;
            }

            int localOrdinal = lookup.getTypedOrdinal(node).orElse(-1);
            if (localOrdinal == -1) return PatchResult.PASS;

            int paramOrdinal = lookup.getParameterOrdinal(node);
            usedOrdinals.add(paramOrdinal);

            if (addOrdinal && cleanLocals.size() > 1) {
                parameterToOrdinal.put(paramOrdinal, localOrdinal);
            }
        }

        Type[] args = Type.getArgumentTypes(methodNode.desc);
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
