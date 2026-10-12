package org.sinytra.adapter.pipeline.processor;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.MixinClassGenerator;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.List;
import java.util.stream.IntStream;

public final class SplitMethodCancellationHelper {

    public static void handle(MixinContext context, Recipe recipe, MethodNode newTarget) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) return;
        TargetPair newCleanTarget = recipe.getNewCleanTarget();
        if (newCleanTarget == null) return;

        ClassNode originalClassTarget = newCleanTarget.classNode();
        MethodNode originalMethodTarget = newCleanTarget.methodNode();

        if (!MethodAnalyzer.isDirtyDeprecatedMethod(cleanTarget.methodNode(), originalMethodTarget) || Type.getReturnType(originalMethodTarget.desc) != Type.VOID_TYPE) {
            return;
        }

        List<MethodNode> invocations = context.dataFlow(newCleanTarget).findTopLevelOwnCalls();
        if (invocations == null) {
            return;
        }
        int index = IntStream.range(0, invocations.size())
            .filter(i -> invocations.get(i).name.equals(newTarget.name) && invocations.get(i).desc.equals(newTarget.desc))
            .findFirst()
            .orElse(-1);
        if (index == -1) {
            return;
        }

        MixinClassGenerator generator = context.patchContext().environment().classGenerator();
        ClassNode generatedTarget = generator.getOrGenerateMixinClass(context.classNode(), originalClassTarget.name, null);

        String fieldName = "adapter$canceller$" + originalMethodTarget.name + "$" + AdapterUtil.randomString(5);
        int fieldAccess = Opcodes.ACC_PRIVATE | (context.isStatic() ? Opcodes.ACC_STATIC : 0);
        FieldNode trackerField = (FieldNode) generatedTarget.visitField(fieldAccess, fieldName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);

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
        boolean isStatic = context.isStatic();
        Type generatedType = Type.getObjectType(generatedTarget.name);
        GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
        Label endLabel = new Label();
        gen.newLabel();
        if (isStatic) {
            gen.getStatic(generatedType, trackerField.name, Type.BOOLEAN_TYPE);
        } else {
            gen.loadThis();
            gen.getField(generatedType, trackerField.name, Type.BOOLEAN_TYPE);
        }
        gen.visitJumpInsn(Opcodes.IFEQ, endLabel);
        {
            if (reset) {
                gen.newLabel();
                if (isStatic) {
                    gen.visitInsn(Opcodes.ICONST_0);
                    gen.putStatic(generatedType, trackerField.name, Type.BOOLEAN_TYPE);
                } else {
                    gen.loadThis();
                    gen.visitInsn(Opcodes.ICONST_0);
                    gen.putField(generatedType, trackerField.name, Type.BOOLEAN_TYPE);
                }
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
        for (MethodInsnNode minsn : MethodAnalyzer.findCalls(context.methodNode(), qualifier)) {
            InsnList list = new InsnList();
            if (isStatic) {
                list.add(new InsnNode(Opcodes.ICONST_1));
                list.add(new FieldInsnNode(Opcodes.PUTSTATIC, originalClassTarget.name, trackerField.name, trackerField.desc));
            } else {
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new InsnNode(Opcodes.ICONST_1));
                list.add(new FieldInsnNode(Opcodes.PUTFIELD, originalClassTarget.name, trackerField.name, trackerField.desc));
            }
            methodInsns.insertBefore(minsn, list);
        }
    }
}
