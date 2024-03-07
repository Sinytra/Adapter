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
    MethodQualifier getTargetMethodQualifier();

    @Nullable
    MethodQualifier getInjectionPointMethodQualifier();

    List<AbstractInsnNode> findInjectionTargetInsns(TargetPair target);

    void updateDescription(List<Type> parameters);

    boolean isStatic();

    @Nullable
    List<LocalVariable> getTargetMethodLocals(TargetPair target);

    @Nullable
    default List<LocalVariable> getTargetMethodLocals(TargetPair target, int startPos) {
        return getTargetMethodLocals(target, startPos, patchContext().environment().fabricLVTCompatibility());
    }

    @Nullable
    List<LocalVariable> getTargetMethodLocals(TargetPair target, int startPos, int lvtCompatLevel);

    List<Integer> getLvtCompatLevelsOrdered();

    record LocalVariable(int index, Type type) {}

    record TargetPair(ClassNode classNode, MethodNode methodNode) {}
}
