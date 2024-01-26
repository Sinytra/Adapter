package dev.su5ed.sinytra.adapter.patch.transformer.dynamic;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.MockMixinRuntime;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.ArrayList;
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
        AnnotationHandle annotation = methodContext.methodAnnotation();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, context.classNode().name, context.environment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, methodNode, annotation.unwrap(), atNode.unwrap());
        if (injectionPoint != null) {
            AnnotationValueHandle<String> target = atNode.<String>getValue("target").orElse(null);
            if (target == null) {
                return Patch.Result.PASS;
            }
            MethodQualifier q = MethodQualifier.create(context.remap(target.get())).filter(MethodQualifier::isFull).orElse(null);
            if (q == null) {
                return Patch.Result.PASS;
            }
            MethodContext.TargetPair targetPair = methodContext.findDirtyInjectionTarget();
            if (targetPair == null) {
                return Patch.Result.PASS;
            }
            List<AbstractInsnNode> insns = new ArrayList<>();
            injectionPoint.find(targetPair.methodNode().desc, targetPair.methodNode().instructions, insns);
            if (!insns.isEmpty()) {
                return Patch.Result.PASS;
            }

            String owner = q.internalOwnerName();
            for (AbstractInsnNode insn : targetPair.methodNode().instructions) {
                if (insn instanceof MethodInsnNode minsn && minsn.name.equals(q.name()) && minsn.desc.equals(q.desc()) && !minsn.owner.equals(owner)) {
                    if (context.environment().inheritanceHandler().isClassInherited(minsn.owner, owner) || isFixedField(minsn, context)) {
                        target.set(MethodCallAnalyzer.getCallQualifier(minsn));
                        Patch.Result result = Patch.Result.APPLY;
                        if (annotation.matchesDesc(MixinConstants.REDIRECT) && (methodNode.access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) {
                            ModifyMethodParams patch = ModifyMethodParams.builder()
                                .replace(0, Type.getObjectType(minsn.owner))
                                .ignoreOffset()
                                .build();
                            result = result.or(patch.apply(classNode, methodNode, methodContext, context));
                        }
                        return result;
                    }
                }
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
