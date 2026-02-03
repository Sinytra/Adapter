package org.sinytra.adapter.patch.resolver.target;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.env.ctx.MixinClassGenerator;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.List;

public final class SplitMethodCancellationHelper {

    public static void handle(MixinContext context, Recipe recipe, MethodNode newTarget) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair originalTarget = recipe.getDirtyTarget();

        ClassNode originalClassTarget = originalTarget.classNode();
        MethodNode originalMethodTarget = originalTarget.methodNode();

        if (!MethodAnalyzer.isDirtyDeprecatedMethod(cleanTarget.methodNode(), originalMethodTarget) || Type.getReturnType(originalMethodTarget.desc) != Type.VOID_TYPE) {
            return;
        }

        MixinClassGenerator generator = context.patchContext().environment().classGenerator();
        ClassNode generatedTarget = generator.getOrGenerateMixinClass(context.classNode(), originalClassTarget.name, null);

        List<MethodNode> invocations = MethodAnalyzer.collectMethodInvocations(originalClassTarget, originalMethodTarget);
        if (invocations == null) {
            return;
        }
        int index = invocations.indexOf(newTarget);

        // Generate field
        String fieldName = "adapter$canceller$" + originalMethodTarget.name + "$" + AdapterUtil.randomString(5);
        FieldNode trackerField = (FieldNode) generatedTarget.visitField(Opcodes.ACC_PRIVATE, fieldName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);

        for (int i = index + 1; i < invocations.size(); i++) {
            generateCancellerMethod(generatedTarget, trackerField, originalClassTarget, invocations.get(i), context, i == invocations.size() - 1);
        }

        context.recordCtxAudit("Generate cancellation handler mixin");
    }

    private static void generateCancellerMethod(ClassNode generatedTarget, FieldNode trackerField, ClassNode originalClassTarget, MethodNode newTarget, MixinContext context, boolean reset) {
        String name = context.methodNode().name + "$adapter$canceller$" + AdapterUtil.randomString(5);
        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, TypeConstants.CI_TYPE);
        MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | (context.isStatic() ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        {
            AnnotationVisitor injectAnn = invokerMixinMethod.visitAnnotation(MixinAnnotations.INJECT, true);
            {
                AnnotationVisitor methodValue = injectAnn.visitArray("method");
                methodValue.visit(null, newTarget.name + newTarget.desc);
                methodValue.visitEnd();
            }
            {
                AnnotationVisitor atValue = injectAnn.visitArray("at");
                {
                    AnnotationVisitor atAnn = atValue.visitAnnotation(null, MixinAnnotations.AT);
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
            gen.invokeVirtual(TypeConstants.CI_TYPE, new Method("cancel", "()V"));
        }
        gen.visitLabel(endLabel);
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        // Modify mixin to set the field value
        MethodQualifier qualifier = new MethodQualifier(TypeConstants.CI_TYPE.getDescriptor(), "cancel", "()V");
        InsnList methodInsns = context.methodNode().instructions;
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
