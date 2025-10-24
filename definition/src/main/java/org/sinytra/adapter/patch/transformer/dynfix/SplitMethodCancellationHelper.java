package org.sinytra.adapter.patch.transformer.dynfix;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinClassGenerator;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public final class SplitMethodCancellationHelper {

    public static void handle(Object transform, MethodContext methodContext, MethodNode newTarget) {
        MethodContext.TargetPair originalTarget = methodContext.findDirtyInjectionTarget();
        ClassNode originalClassTarget = originalTarget.classNode();
        MethodNode originalMethodTarget = originalTarget.methodNode();

        if (!MethodCallAnalyzer.isDirtyDeprecatedMethod(methodContext.findCleanInjectionTarget().methodNode(), originalMethodTarget) || Type.getReturnType(originalMethodTarget.desc) != Type.VOID_TYPE) {
            return;
        }

        MixinClassGenerator generator = methodContext.patchContext().environment().classGenerator();
        ClassNode generatedTarget = generator.getOrGenerateMixinClass(methodContext.getMixinClass(), originalClassTarget.name, null);

        List<MethodNode> invocations = MethodCallAnalyzer.collectMethodInvocations(originalClassTarget, originalMethodTarget);
        if (invocations == null) {
            return;
        }
        int index = invocations.indexOf(newTarget);

        // Generate field
        String fieldName = "adapter$canceller$" + originalMethodTarget.name + "$" + AdapterUtil.randomString(5);
        FieldNode trackerField = (FieldNode) generatedTarget.visitField(Opcodes.ACC_PRIVATE, fieldName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);

        for (int i = index + 1; i < invocations.size(); i++) {
            generateCancellerMethod(generatedTarget, trackerField, originalClassTarget, invocations.get(i), methodContext, i == invocations.size() - 1);
        }

        methodContext.recordAudit(transform, "Generate cancellation handler mixin");
    }

    private static void generateCancellerMethod(ClassNode generatedTarget, FieldNode trackerField, ClassNode originalClassTarget, MethodNode newTarget, MethodContext methodContext, boolean reset) {
        String name = methodContext.getMixinMethod().name + "$adapter$canceller$" + AdapterUtil.randomString(5);
        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, AdapterUtil.CI_TYPE);
        MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | (methodContext.isStatic() ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        {
            AnnotationVisitor injectAnn = invokerMixinMethod.visitAnnotation(MixinConstants.INJECT, true);
            {
                AnnotationVisitor methodValue = injectAnn.visitArray("method");
                methodValue.visit(null, newTarget.name + newTarget.desc);
                methodValue.visitEnd();
            }
            {
                AnnotationVisitor atValue = injectAnn.visitArray("at");
                {
                    AnnotationVisitor atAnn = atValue.visitAnnotation(null, MixinConstants.AT);
                    atAnn.visit("value", "HEAD");
                    atAnn.visitEnd();
                }
                atValue.visitEnd();
            }
            injectAnn.visit("cancellable", Boolean.TRUE);
            injectAnn.visitEnd();
        }
        // Generate logic
        GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
        Label endLabel = new Label();
        gen.newLabel();
        gen.loadThis();
        gen.getField(Type.getObjectType(generatedTarget.name), trackerField.name, Type.BOOLEAN_TYPE);
        gen.visitJumpInsn(Opcodes.IFEQ, endLabel);
        {
            if (reset) {
                gen.newLabel();
                gen.loadThis();
                gen.visitInsn(Opcodes.ICONST_0);
                gen.putField(Type.getObjectType(generatedTarget.name), trackerField.name, Type.BOOLEAN_TYPE);
            }
            gen.newLabel();
            gen.loadArg(0);
            gen.invokeVirtual(AdapterUtil.CI_TYPE, new Method("cancel", "()V"));
        }
        gen.visitLabel(endLabel);
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        // Modify mixin to set the field value
        MethodQualifier qualifier = new MethodQualifier(AdapterUtil.CI_TYPE.getDescriptor(), "cancel", "()V");
        InsnList methodInsns = methodContext.getMixinMethod().instructions;
        for (AbstractInsnNode insn : methodInsns) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new InsnNode(Opcodes.ICONST_1));
                list.add(new FieldInsnNode(Opcodes.PUTFIELD, originalClassTarget.name, trackerField.name, trackerField.desc));
                methodInsns.insertBefore(minsn, list);
            }
        }
    }
}
