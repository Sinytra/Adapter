package org.sinytra.adapter.types;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.util.AdapterUtil;

public class FieldAccessorTypeTransformer implements MethodTransformer {
    private static final String PREFIX = "adapter$";
    private static final int PRIORITY_MAX = 9999;

    @Override
    public PatchResult apply(MixinContext context, Configuration config) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (bfu == null || context.targetTypes().size() != 1) return PatchResult.PASS;

        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        String fieldFqn = AdapterUtil.getAccessorTargetFieldName(classNode.name, methodNode, context.methodAnnotation(), context.environment())
            .orElse(null);
        if (fieldFqn == null) return PatchResult.PASS;

        String fieldName = getFieldName(fieldFqn);
        Type owner = context.targetTypes().getFirst();
        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(owner.getInternalName(), fieldName);
        if (updatedTypes != null) {
            TypeAdapter typeAdapter = bfu.getTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
            if (typeAdapter != null) {
                String targetMethod = addRedirectAcceptorField(owner, methodNode, fieldName, typeAdapter, bfu.getGenerator());

                // Change Accessor to Invoker
                methodNode.visibleAnnotations.remove(context.methodAnnotation().unwrap());
                AnnotationVisitor invokerAnn = methodNode.visitAnnotation(MixinAnnotations.INVOKER, true);
                invokerAnn.visit("value", targetMethod);
                return PatchResult.APPLY;
            }
        }

        return PatchResult.PASS;
    }

    private String addRedirectAcceptorField(Type owner, MethodNode methodNode, String field, TypeAdapter adapter, BytecodeFixerJarGenerator generator) {
        ClassNode node = getOrCreateMixinClass(owner, generator);

        String methodName = PREFIX + field;
        Type to = adapter.to();
        String methodDesc = Type.getMethodDescriptor(to);
        if (node.methods.stream().noneMatch(m -> m.name.equals(methodName))) {
            boolean isStatic = MethodHelper.isStatic(methodNode);
            MethodNode method = (MethodNode) node.visitMethod(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0), methodName, methodDesc, null, null);
            {
                AnnotationVisitor annotationVisitor = method.visitAnnotation(MixinAnnotations.UNIQUE, true);
                annotationVisitor.visitEnd();
            }
            {
                method.visitCode();
                if (!isStatic) {
                    method.visitVarInsn(Opcodes.ALOAD, 0);
                }
                method.visitFieldInsn(isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner.getInternalName(), field, adapter.from().getDescriptor());
                adapter.apply(method.instructions, method.instructions.getLast());
                method.visitInsn(getReturnOpcode(to));
                method.visitEnd();
            }
            method.visitEnd();
        }

        return methodName + methodDesc;
    }

    private ClassNode getOrCreateMixinClass(Type targetClass, BytecodeFixerJarGenerator generator) {
        String className = targetClass.getInternalName().replace('/', '_');
        return generator.getOrCreateClass(className, s -> generateFieldAdapterMixin(s, targetClass));
    }

    private ClassNode generateFieldAdapterMixin(String className, Type targetClass) {
        ClassNode node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);

        {
            AnnotationVisitor mixinAnnotation = node.visitAnnotation(MixinAnnotations.MIXIN, false);
            {
                AnnotationVisitor valueVisitor = mixinAnnotation.visitArray("value");
                valueVisitor.visit(null, targetClass);
                valueVisitor.visitEnd();
            }
            mixinAnnotation.visit("priority", PRIORITY_MAX);
            mixinAnnotation.visitEnd();
        }

        return node;
    }

    private static int getReturnOpcode(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> Opcodes.IRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.LONG -> Opcodes.LRETURN;
            case Type.VOID -> Opcodes.RETURN;
            default -> throw new UnsupportedOperationException();
        };
    }

    private static String getFieldName(String desc) {
        int descIndex = desc.indexOf(':');
        return descIndex == -1 ? desc : desc.substring(0, descIndex);
    }
}
