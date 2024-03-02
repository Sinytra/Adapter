package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public interface MethodContext {
    AnnotationValueHandle<?> classAnnotation();

    AnnotationHandle methodAnnotation();

    @Nullable AnnotationHandle injectionPointAnnotation();

    AnnotationHandle injectionPointAnnotationOrThrow();

    List<Type> targetTypes();

    List<String> matchingTargets();

    PatchContext patchContext();

    TargetPair findCleanInjectionTarget();

    TargetPair findDirtyInjectionTarget();

    @Nullable
    MethodQualifier getTargetMethodQualifier(PatchContext context);

    @Nullable
    MethodQualifier getInjectionPointMethodQualifier(PatchContext context);

    List<AbstractInsnNode> findInjectionTargetInsns(ClassNode classNode, ClassNode targetClass, MethodNode methodNode, MethodNode targetMethod, PatchContext context);

    void updateDescription(ClassNode classNode, MethodNode methodNode, List<Type> parameters);

    boolean isStatic(MethodNode methodNode);

    @Nullable
    List<LocalVariable> getTargetMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod);

    @Nullable
    List<LocalVariable> getTargetMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod, int startPos);
    
    record LocalVariable(int index, Type type) {}

    record TargetPair(ClassNode classNode, MethodNode methodNode) {};
}
