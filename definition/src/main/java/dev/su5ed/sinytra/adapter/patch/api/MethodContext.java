package dev.su5ed.sinytra.adapter.patch.api;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public interface MethodContext {
    AnnotationValueHandle<?> classAnnotation();

    AnnotationHandle methodAnnotation();

    @Nullable AnnotationHandle injectionPointAnnotation();

    AnnotationHandle injectionPointAnnotationOrThrow();

    List<Type> targetTypes();

    List<String> matchingTargets();

    PatchContext patchContext();

    Pair<ClassNode, MethodNode> findCleanInjectionTarget();

    Pair<ClassNode, MethodNode> findDirtyInjectionTarget();

    @Nullable
    MethodQualifier getTargetMethodQualifier(PatchContext context);

    @Nullable
    MethodQualifier getInjectionPointMethodQualifier(PatchContext context);

    List<AbstractInsnNode> findInjectionTargetInsns(ClassNode classNode, ClassNode targetClass, MethodNode methodNode, MethodNode targetMethod, PatchContext context);

    void updateDescription(ClassNode classNode, MethodNode methodNode, List<Type> parameters);
}
