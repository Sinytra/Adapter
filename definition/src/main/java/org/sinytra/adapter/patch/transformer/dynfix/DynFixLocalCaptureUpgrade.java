package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;

public class DynFixLocalCaptureUpgrade implements DynamicFixer<DynFixLocalCaptureUpgrade.Data> {
    public record Data(AdapterUtil.CapturedLocals capturedLocals, LocalVarAnalyzer.CapturedLocalsTransform transform) {}

    @Override
    @Nullable
    public DynFixLocalCaptureUpgrade.Data prepare(MethodContext methodContext) {
        if (methodContext.findDirtyInjectionTarget() == null) {
            return null;
        }
        MethodNode methodNode = methodContext.getMixinMethod();
        if (!methodContext.capturesLocals()) {
            return null;
        }
        Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
        List<Pair<AnnotationNode, Type>> localAnnotations = AdapterUtil.getAnnotatedParameters(methodNode, paramTypes, MixinConstants.LOCAL, Pair::of);
        if (!localAnnotations.isEmpty()) {
            return null;
        }

        LocalVarAnalyzer.CapturedLocalsInfo info = LocalVarAnalyzer.getCapturedLocals(methodContext);
        if (info == null || info.diff().isEmpty()) {
            return null;
        }

        LocalVarAnalyzer.CapturedLocalsTransform transform = LocalVarAnalyzer.analyzeCapturedLocals(info.capturedLocals(), methodNode);
        List<Type> availableTypes = new ArrayList<>(info.availableTypes());
        for (LocalVariableNode node : transform.usedLocalNodes()) {
            Type expected = Type.getType(node.desc);
            List<Type> available = availableTypes.stream().filter(expected::equals).toList();
            if (available.size() != 1) {
                return null;
            }
            availableTypes.remove(available.getFirst());
        }

        return new Data(info.capturedLocals(), transform);
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        Patch.Result result = data.transform().remover().apply(methodContext);
        if (result == Patch.Result.PASS) {
            return null;
        }
        
        int start = data.capturedLocals().paramLocalStart();
        Type[] args = Type.getArgumentTypes(methodNode.desc);

        for (int i = start; i < args.length; i++) {
            methodNode.visitParameterAnnotation(i, MixinConstants.LOCAL, false);
        }

        auditTrail.recordAudit(this, methodContext, "Upgrade captured locals");

        return FixResult.of(result, PatchAuditTrail.Match.FULL);
    }
}
