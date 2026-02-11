package org.sinytra.adapter.transform.preprocess;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_ORDINAL;

public class LocalCaptureUpgradeTransformer implements MethodTransformer {
    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        // Check requirements
        TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), config.getTargetMethod());
        if (cleanTarget == null) return PatchResult.PASS;

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), config.getTargetMethod());
        if (dirtyTarget == null) return PatchResult.PASS;

        if (!config.hasProperty(MixinKeys.LOCALS)) return PatchResult.PASS;

        // Analyze locals
        MethodNode methodNode = context.methodNode();
        Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
        List<Pair<AnnotationNode, Type>> localAnnotations = AdapterUtil.getAnnotatedParameters(methodNode, paramTypes, MixinAnnotations.LOCAL, Pair::of);
        if (!localAnnotations.isEmpty()) return PatchResult.PASS;

        LocalVarAnalyzer.CapturedLocalsInfo info = LocalVarAnalyzer.getCapturedLocals(context, dirtyTarget);
        if (info == null || info.diff().isEmpty()) return PatchResult.PASS;

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);

        LocalVariableLookup cleanLookup = new LocalVariableLookup(cleanTarget.methodNode());
        LocalVariableLookup dirtyLookup = new LocalVariableLookup(dirtyTarget.methodNode());
        LocalVariableLookup lookup = info.capturedLocals().lvt();

        Map<Integer, Integer> parameterToOrdinal = new HashMap<>();
        Set<Integer> usedOrdinals = new HashSet<>();
        for (LocalVariableNode node : transform.usedLocalNodes()) {
            Type expected = Type.getType(node.desc);

            List<LocalVariableNode> cleanLocals = cleanLookup.getForType(expected);
            List<LocalVariableNode> dirtyLocals = dirtyLookup.getForType(expected);
            if (cleanLocals.size() != dirtyLocals.size())
                return PatchResult.PASS;

            List<LocalVariableNode> sameType = methodNode.localVariables.stream()
                .filter(l -> Type.getType(l.desc).equals(expected))
                .toList();
            int localOrdinal = sameType.indexOf(node);
            if (localOrdinal == -1) return PatchResult.PASS;

            int paramOrdinal = lookup.getParameterOrdinal(node);
            parameterToOrdinal.put(paramOrdinal, localOrdinal);
            usedOrdinals.add(paramOrdinal);
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

        PatchResult result = transform.remover().apply(context);
        if (result == PatchResult.PASS) return PatchResult.PASS;

        context.recordCtxAudit("Upgrade captured locals");
        context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
        return result;
    }
}
