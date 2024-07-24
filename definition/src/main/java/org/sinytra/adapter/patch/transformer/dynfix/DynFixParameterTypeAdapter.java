package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.BundledMethodTransform;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformTarget;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DynFixParameterTypeAdapter implements DynamicFixer<DynFixParameterTypeAdapter.Data> {
    public record Data(MethodInsnNode newCall, ParamsDiffSnapshot diff, Set<List<AbstractInsnNode>> modifyCalls, MethodQualifier qualifier) {}

    @Nullable
    @Override
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.REDIRECT)) {
            MethodQualifier qualifier = methodContext.getInjectionPointMethodQualifier();
            if (qualifier == null) {
                return null;
            }
            MethodContext.TargetPair dirtyPair = methodContext.findDirtyInjectionTarget();
            if (dirtyPair == null) {
                return null;
            }
            List<MethodInsnNode> newMethodCalls = StreamSupport.stream(dirtyPair.methodNode().instructions.spliterator(), false)
                .filter(i -> i instanceof MethodInsnNode minsn && minsn.owner.equals(qualifier.internalOwnerName()) && minsn.name.equals(qualifier.name()) && !minsn.desc.equals(qualifier.desc()))
                .map(i -> (MethodInsnNode) i)
                .toList();
            if (newMethodCalls.size() != 1) {
                return null;
            }
            MethodInsnNode newCall = newMethodCalls.getFirst();
            LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(List.of(Type.getArgumentTypes(qualifier.desc())), List.of(Type.getArgumentTypes(newCall.desc)));
            Set<List<AbstractInsnNode>> modifyCalls = validateReplacements(methodContext, qualifier, diff.replacements());
            if (modifyCalls == null) {
                return null;
            }
            return new Data(newCall, diff, modifyCalls, qualifier);
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        Patch.Result result = BundledMethodTransform.builder()
            .modifyInjectionPoint(MethodCallAnalyzer.getCallQualifier(data.newCall()))
            .transform(data.diff().asParameterTransformer(ParamTransformTarget.INJECTION_POINT, false))
            .apply(methodContext);
        if (result == Patch.Result.PASS) {
            return null;
        }

        MethodQualifier qualifier = data.qualifier();
        for (List<AbstractInsnNode> call : data.modifyCalls()) {
            for (AbstractInsnNode insn : call) {
                if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                    minsn.desc = data.newCall().desc;
                }
            }
        }

        return FixResult.of(result.or(Patch.Result.APPLY), PatchAuditTrail.Match.FULL);
    }

    @Nullable
    private static Set<List<AbstractInsnNode>> validateReplacements(MethodContext methodContext, MethodQualifier qualifier, List<Pair<Integer, Type>> replacements) {
        MethodNode mixinMethod = methodContext.getMixinMethod();
        List<List<AbstractInsnNode>> insns = MethodCallAnalyzer.getInvocationInsns(mixinMethod, qualifier);

        LocalVariableLookup lookup = new LocalVariableLookup(methodContext.getMixinMethod());
        Set<Integer> paramVars = replacements.stream().map(p -> lookup.getByParameterOrdinal(p.getFirst()).index).collect(Collectors.toSet());

        Set<List<AbstractInsnNode>> usedInsns = new HashSet<>();
        for (AbstractInsnNode insn : methodContext.getMixinMethod().instructions) {
            if (insn instanceof VarInsnNode varInsn && paramVars.contains(varInsn.var)) {
                List<AbstractInsnNode> call = insns.stream().filter(l -> l.contains(varInsn)).findFirst().orElse(null);
                if (call == null) {
                    return null;
                }
                usedInsns.add(call);
            }
        }

        return usedInsns;
    }
}
