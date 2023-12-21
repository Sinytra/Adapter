package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ExtractMixin(String targetClass) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.WRAP_OPERATION, MixinConstants.MODIFY_CONST, MixinConstants.MODIFY_ARG, MixinConstants.INJECT, MixinConstants.REDIRECT, MixinConstants.MODIFY_VAR, MixinConstants.MODIFY_EXPR_VAL);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        // Sanity check
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        MethodQualifier qualifier = methodContext.getTargetMethodQualifier(context);
        if (qualifier == null) {
            return Patch.Result.PASS;
        }
        String owner = Objects.requireNonNullElse(qualifier.internalOwnerName(), this.targetClass);
        boolean isInherited = context.environment().inheritanceHandler().isClassInherited(this.targetClass, owner);
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name) && !isInheritedField(classNode, finsn, isInherited)
                || insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name) && !isInheritedMethod(classNode, minsn, isInherited)
            ) {
                // We can't move methods that access their class instance
                return Patch.Result.PASS;
            }
        }
        ClassNode targetClass = AdapterUtil.getClassNode(this.targetClass);
        // Get or generate new mixin class
        ClassNode generatedTarget = context.environment().classGenerator().getOrGenerateMixinClass(classNode, this.targetClass, targetClass != null ? targetClass.superName : null);
        context.environment().refmapHolder().copyEntries(classNode.name, generatedTarget.name);
        // Add mixin method from original to generated class
        generatedTarget.methods.add(methodNode);
        // Remove original method
        context.postApply(() -> classNode.methods.remove(methodNode));
        if (!isStatic && methodNode.localVariables != null) {
            methodNode.localVariables.stream().filter(l -> l.index == 0).findFirst().ifPresent(lvn -> {
                lvn.desc = Type.getObjectType(generatedTarget.name).getDescriptor();
            });
        }
        return Patch.Result.APPLY;
    }

    private static boolean isInheritedField(ClassNode cls, FieldInsnNode finsn, boolean isTargetInherited) {
        FieldNode field = cls.fields.stream()
            .filter(f -> f.name.equals(finsn.name))
            .findFirst()
            .orElse(null);
        if (field != null) {
            if (AdapterUtil.isShadowField(field)) {
                return true;
            }
            if (isTargetInherited && finsn.getOpcode() != Opcodes.GETSTATIC) {
                field.access = fixAccess(field.access);
                return true;
            }
        }
        return false;
    }

    private static boolean isInheritedMethod(ClassNode cls, MethodInsnNode minsn, boolean isTargetInherited) {
        MethodNode method = cls.methods.stream()
            .filter(m -> m.name.equals(minsn.name) && m.desc.equals(minsn.desc))
            .findFirst()
            .orElse(null);
        if (method != null) {
            List<AnnotationNode> annotations = method.visibleAnnotations != null ? method.visibleAnnotations : List.of();
            if (AdapterUtil.hasAnnotation(annotations, MixinConstants.SHADOW)) {
                return true;
            }
            if (isTargetInherited && minsn.getOpcode() != Opcodes.INVOKESTATIC) {
                method.access = fixAccess(method.access);
                return true;
            }
        }
        return false;
    }

    private static int fixAccess(int access) {
        int visibility = access & 0x7;
        // Lower than protected
        if (visibility == Opcodes.ACC_PRIVATE || visibility == 0) {
            // Widen to protected
            return access & ~0x7 | Opcodes.ACC_PROTECTED;
        }
        return visibility;
    }
}
