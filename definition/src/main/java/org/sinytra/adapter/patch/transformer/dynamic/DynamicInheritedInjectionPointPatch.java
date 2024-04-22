package org.sinytra.adapter.patch.transformer.dynamic;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.List;

public class DynamicInheritedInjectionPointPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle atNode = methodContext.injectionPointAnnotation();
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            return Patch.Result.PASS;
        }
        if (atNode.<String>getValue("value").map(v -> !v.get().equals("INVOKE")).orElse(true)) {
            return Patch.Result.PASS;
        }
        AnnotationValueHandle<String> target = atNode.<String>getValue("target").orElse(null);
        if (target == null) {
            return Patch.Result.PASS;
        }
        MethodQualifier q = methodContext.getInjectionPointMethodQualifier();
        if (q == null) {
            return Patch.Result.PASS;
        }
        MethodContext.TargetPair targetPair = methodContext.findDirtyInjectionTarget();
        if (targetPair == null) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> insns = methodContext.findInjectionTargetInsns(targetPair);
        if (!insns.isEmpty()) {
            return Patch.Result.PASS;
        }
        String owner = q.internalOwnerName();
        for (AbstractInsnNode insn : targetPair.methodNode().instructions) {
            if (insn instanceof MethodInsnNode minsn && minsn.name.equals(q.name()) && minsn.desc.equals(q.desc()) && !minsn.owner.equals(owner)
                && (context.environment().inheritanceHandler().isClassInherited(minsn.owner, owner) || isFixedField(minsn, context))
            ) {
                target.set(MethodCallAnalyzer.getCallQualifier(minsn));
                if (methodContext.methodAnnotation().matchesDesc(MixinConstants.REDIRECT) && minsn.getOpcode() != Opcodes.INVOKESTATIC) {
                    methodNode.visitParameterAnnotation(0, MixinConstants.COERCE, false);
                }
                return Patch.Result.APPLY;
            }
        }
        return Patch.Result.PASS;
    }

    private boolean isFixedField(AbstractInsnNode insn, PatchContext context) {
        for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
            if (prev instanceof LabelNode) {
                break;
            }
            if (prev instanceof FieldInsnNode finsn) {
                BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
                return bfu.getFieldTypeChange(finsn.owner, GlobalReferenceMapper.remapReference(finsn.name)) != null;
            }
        }
        return false;
    }
}
