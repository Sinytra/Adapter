package org.sinytra.adapter.types;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodHelper;
import org.sinytra.adapter.api.BytecodeFixerUpper;
import org.sinytra.adapter.api.ClassTransformer;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.AnnotationValueHandle;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.pipeline.ctx.PatchContext;
import org.sinytra.adapter.util.OpcodeUtil;
import org.spongepowered.asm.mixin.gen.AccessorInfo;

import java.util.Optional;

public class FieldAccessorTypeTransformer implements ClassTransformer {
    private static final String PREFIX = "adapter$";
    private static final int PRIORITY_MAX = 9999;

    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (bfu == null || context.targetTypes().size() != 1) return PatchResult.PASS;

        Type owner = context.targetTypes().getFirst();
        PatchResult result = PatchResult.PASS;
        for (MethodNode methodNode : classNode.methods) {
            AnnotationNode accessor = methodNode.visibleAnnotations == null ? null : methodNode.visibleAnnotations.stream()
                .filter(a -> MixinAnnotations.ACCESSOR.equals(a.desc))
                .findFirst()
                .orElse(null);
            if (accessor == null || Type.getArgumentCount(methodNode.desc) != 0 || Type.getReturnType(methodNode.desc) == Type.VOID_TYPE) {
                continue;
            }
            result = result.or(redirectAccessor(classNode, methodNode, accessor, owner, bfu, context));
        }
        return result;
    }

    private PatchResult redirectAccessor(ClassNode classNode, MethodNode methodNode, AnnotationNode accessor, Type owner, BytecodeFixerUpper bfu, PatchContext context) {
        String fieldFqn = getAccessorTargetFieldName(classNode.name, methodNode, new AnnotationHandle(accessor), context.environment())
            .orElse(null);
        if (fieldFqn == null) return PatchResult.PASS;

        String fieldName = getFieldName(fieldFqn);
        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(owner.getInternalName(), fieldName);
        if (updatedTypes == null) return PatchResult.PASS;

        TypeAdapter typeAdapter = bfu.getTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
        if (typeAdapter == null) return PatchResult.PASS;

        String targetMethod = addRedirectAcceptorField(owner, methodNode, fieldName, typeAdapter, bfu.getGenerator());

        // Change Accessor to Invoker
        methodNode.visibleAnnotations.remove(accessor);
        AnnotationVisitor invokerAnn = methodNode.visitAnnotation(MixinAnnotations.INVOKER, true);
        invokerAnn.visit("value", targetMethod);
        context.environment().auditTrail().recordAudit(this, classNode, "Redirect accessor %s%s to invoker %s", methodNode.name, methodNode.desc, targetMethod);

        return PatchResult.APPLY;
    }

    private String addRedirectAcceptorField(Type owner, MethodNode methodNode, String field, TypeAdapter adapter, BytecodeFixerJarGenerator generator) {
        ClassNode node = getOrCreateMixinClass(owner, generator);

        String methodName = PREFIX + field;
        Type to = adapter.to();
        String methodDesc = Type.getMethodDescriptor(to);
        synchronized (node) {
            addRedirectAcceptorMethod(node, owner, methodNode, field, adapter, methodName, to, methodDesc);
        }

        return methodName + methodDesc;
    }

    private static void addRedirectAcceptorMethod(ClassNode node, Type owner, MethodNode methodNode, String field, TypeAdapter adapter, String methodName, Type to, String methodDesc) {
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
                method.visitInsn(OpcodeUtil.getReturnOpcode(to));
                method.visitEnd();
            }
            method.visitEnd();
        }
    }

    private ClassNode getOrCreateMixinClass(Type targetClass, BytecodeFixerJarGenerator generator) {
        String className = targetClass.getInternalName().replace('/', '_');
        return generator.getOrCreateClass(className, s -> generateFieldAdapterMixin(s, targetClass));
    }

    private ClassNode generateFieldAdapterMixin(String className, Type targetClass) {
        ClassNode node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null, TypeConstants.OBJECT_TYPE.getInternalName(), null);

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

    private static String getFieldName(String desc) {
        int descIndex = desc.indexOf(':');
        return descIndex == -1 ? desc : desc.substring(0, descIndex);
    }

    private static Optional<String> getAccessorTargetFieldName(String owner, MethodNode method, AnnotationHandle annotationHandle, PatchEnvironment environment) {
        return annotationHandle.<String>getValue("value")
            .map(AnnotationValueHandle::get)
            .filter(str -> !str.isEmpty())
            .or(() -> Optional.ofNullable(AccessorInfo.AccessorName.of(method.name))
                .map(name -> environment.refmapHolder().remap(owner, name.name)));
    }
}
