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
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.env.util.TypeConstants;

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

        // Re-derive against the injection point the pipeline actually produced (MixinContext#effectiveAtData).
        LocalVarAnalyzer.CapturedLocalsInfo info = LocalVarAnalyzer.getCapturedLocals(context, dirtyTarget, context.effectiveAtData());
        if (info == null) {
            // effectiveAtData is null when the pipeline never published the injection point. A handler whose
            // CallbackInfo is its last parameter captures nothing; anything else is left unmarked for Patcher.
            if (capturesNoLocals(methodNode)) {
                markUpgraded(config);
            }
            return PatchResult.PASS;
        }

        if (info.diff().isEmpty()) {
            // The compiled capture list still matches the patched method - nothing to re-derive.
            markUpgraded(config);
            return PatchResult.PASS;
        }

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);

        LocalVariableLookup cleanLookup = context.methods().getLVT(cleanTarget.methodNode());
        LocalVariableLookup dirtyLookup = context.methods().getLVT(dirtyTarget.methodNode());
        LocalVariableLookup lookup = info.capturedLocals().lvt();

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
                if (matching != 1) return PatchResult.PASS;

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
        ensureParameterAnnotationCapacity(methodNode, args.length);
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

        // Mark the capture as re-derived so Patcher knows this handler was handled.
        markUpgraded(config);

        context.recordCtxAudit("Upgrade captured locals");
        context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
        return result;
    }

    /** Resizes the parameter-annotation arrays so they cover the current parameter count. */
    @SuppressWarnings("unchecked")
    private static void ensureParameterAnnotationCapacity(MethodNode methodNode, int paramCount) {
        methodNode.visibleParameterAnnotations = resizeParameterAnnotations(methodNode.visibleParameterAnnotations, paramCount);
        methodNode.invisibleParameterAnnotations = resizeParameterAnnotations(methodNode.invisibleParameterAnnotations, paramCount);
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] resizeParameterAnnotations(List<AnnotationNode>[] annotations, int paramCount) {
        if (annotations == null || annotations.length == paramCount) {
            return annotations;
        }
        List<AnnotationNode>[] resized = (List<AnnotationNode>[]) new List<?>[paramCount];
        System.arraycopy(annotations, 0, resized, 0, Math.min(annotations.length, paramCount));
        return resized;
    }

    /** Whether this injector declares no captured locals at all, i.e. its CallbackInfo is the last parameter. */
    private static boolean capturesNoLocals(MethodNode methodNode) {
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        for (int i = 0; i < params.length; i++) {
            boolean isCallback = params[i].equals(TypeConstants.CI_TYPE) || params[i].equals(TypeConstants.CIR_TYPE);
            if (isCallback) {
                return i + 1 >= params.length;
            }
        }
        return false;
    }

    /** Records that this handler's capture list is known to line up with the patched method. */
    private static void markUpgraded(Configuration config) {
        if (config instanceof MutableConfiguration mutable) {
            mutable.setProperty(SpecialKeys.LOCALS_UPGRADED, true);
        }
    }
}






