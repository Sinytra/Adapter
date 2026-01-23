package org.sinytra.adapter.next.transform.preprocess;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.patch.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.api.PatchResult;
import org.sinytra.adapter.patch.api.TargetPair;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;

public class LocalCaptureUpgradeTransformer implements MethodTransformer {
    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        // Check requirements
        TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), config.getTargetMethod());
        if (cleanTarget == null) return PatchResult.PASS;

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), config.getTargetMethod());
        if (dirtyTarget == null) return PatchResult.PASS;

        if (!config.hasProperty(Keys.LOCALS)) return PatchResult.PASS;

        // Analyze locals
        MethodNode methodNode = context.methodNode();
        Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
        List<Pair<AnnotationNode, Type>> localAnnotations = AdapterUtil.getAnnotatedParameters(methodNode, paramTypes, MixinConstants.LOCAL, Pair::of);
        if (!localAnnotations.isEmpty()) {
            return PatchResult.PASS;
        }

        LocalVarAnalyzer.CapturedLocalsInfo info = LocalVarAnalyzer.getCapturedLocals(context.legacy());
        if (info == null || info.diff().isEmpty()) {
            return PatchResult.PASS;
        }

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);
        List<Type> availableTypes = new ArrayList<>(info.availableTypes());
        for (LocalVariableNode node : transform.usedLocalNodes()) {
            Type expected = Type.getType(node.desc);
            List<Type> available = availableTypes.stream().filter(expected::equals).toList();
            if (available.size() != 1) {
                return PatchResult.PASS;
            }
            availableTypes.remove(available.getFirst());
        }

        PatchResult result = transform.remover().apply(context.legacy());
        if (result == PatchResult.PASS) return PatchResult.PASS;

        int start = info.capturedLocals().paramLocalStart();
        Type[] args = Type.getArgumentTypes(methodNode.desc);
        for (int i = start; i < args.length; i++) {
            methodNode.visitParameterAnnotation(i, MixinConstants.LOCAL, false);
        }
        
        PatchAuditTrail auditTrail = context.environment().auditTrail();
//        auditTrail.recordAudit(this, methodContext, "Upgrade captured locals");
        auditTrail.recordResult(context.legacy(), PatchAuditTrail.Match.FULL);
        return result;
    }
}
