package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.MockMixinRuntime;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, context.getClassNode().name, context.getEnvironment());
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
            Pair<ClassNode, MethodNode> targetPair = methodContext.findInjectionTarget(context, AdapterUtil::getClassNode);
            if (targetPair == null) {
                return Patch.Result.PASS;
            }
            List<AbstractInsnNode> insns = new ArrayList<>();
            injectionPoint.find(targetPair.getSecond().desc, targetPair.getSecond().instructions, insns);
            if (!insns.isEmpty()) {
                return Patch.Result.PASS;
            }

            String owner = q.internalOwnerName();
            for (AbstractInsnNode insn : targetPair.getSecond().instructions) {
                if (insn instanceof MethodInsnNode minsn && minsn.name.equals(q.name()) && minsn.desc.equals(q.desc()) && !minsn.owner.equals(owner)) {
                    if (context.getEnvironment().getInheritanceHandler().isClassInherited(minsn.owner, owner)) {
                        target.set(MethodCallAnalyzer.getCallQualifier(minsn));
                        Patch.Result result = Patch.Result.APPLY;
                        if (annotation.matchesDesc(Patch.REDIRECT) && (methodNode.access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) {
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
}
