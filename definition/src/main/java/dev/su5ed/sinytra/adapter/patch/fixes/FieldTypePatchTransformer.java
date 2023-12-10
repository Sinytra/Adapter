package dev.su5ed.sinytra.adapter.patch.fixes;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.selector.FieldMatcher;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Set;

public class FieldTypePatchTransformer implements MethodTransform {
    private static final String UNIQUE_ANN = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String PREFIX = "adapter$";

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.ACCESSOR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        BytecodeFixerUpper bfu = context.getEnvironment().getBytecodeFixerUpper();
        if (bfu != null && methodContext.targetTypes().size() == 1) {
            String fieldFqn = AdapterUtil.getAccessorTargetFieldName(classNode.name, methodNode, methodContext.methodAnnotation(), context.getEnvironment()).orElse(null);
            if (fieldFqn != null) {
                String fieldName = new FieldMatcher(fieldFqn).getName();
                Type owner = methodContext.targetTypes().get(0);
                Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(owner.getInternalName(), fieldName);
                if (updatedTypes != null) {
                    FieldTypeFix typeAdapter = bfu.getFieldTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
                    if (typeAdapter != null) {
                        String targetMethod = addRedirectAcceptorField(owner, methodNode, fieldName, typeAdapter, bfu.getGenerator());

                        methodNode.visibleAnnotations.remove(methodContext.methodAnnotation().unwrap());
                        AnnotationVisitor invokerAnn = methodNode.visitAnnotation(Patch.INVOKER, true);
                        invokerAnn.visit("value", targetMethod);
                        return Patch.Result.APPLY;
                    }
                }
            }
        }
        return Patch.Result.PASS;
    }

    private String addRedirectAcceptorField(Type owner, MethodNode methodNode, String field, FieldTypeFix adapter, BytecodeFixerJarGenerator generator) {
        ClassNode node = getOrCreateMixinClass(owner, generator);

        String methodName = PREFIX + field;
        Type to = adapter.to();
        String methodDesc = Type.getMethodDescriptor(to);
        if (node.methods.stream().noneMatch(m -> m.name.equals(methodName))) {
            boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
            MethodNode method = (MethodNode) node.visitMethod(Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0), methodName, methodDesc, null, null);
            {
                AnnotationVisitor annotationVisitor = method.visitAnnotation(UNIQUE_ANN, true);
                annotationVisitor.visitEnd();
            }
            {
                method.visitCode();
                if (!isStatic) {
                    method.visitVarInsn(Opcodes.ALOAD, 0);
                }
                method.visitFieldInsn(isStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner.getInternalName(), field, adapter.from().getDescriptor());
                adapter.typePatch().apply(method.instructions, method.instructions.getLast());
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
            AnnotationVisitor mixinAnnotation = node.visitAnnotation(PatchInstance.MIXIN_ANN, false);
            {
                AnnotationVisitor valueVisitor = mixinAnnotation.visitArray("value");
                valueVisitor.visit(null, targetClass);
                valueVisitor.visitEnd();
            }
            mixinAnnotation.visit("priority", 9999);
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
}
