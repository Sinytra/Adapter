package org.sinytra.adapter.patch.api;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ctx.Auditor;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.List;

public interface MethodContext extends Auditor {
    ClassNode getMixinClass();

    MethodNode getMixinMethod();

    AnnotationHandle methodAnnotation();

    @Nullable AnnotationHandle injectionPointAnnotation();

    PatchContext patchContext();

    TargetPair findCleanInjectionTarget();

    TargetPair findDirtyInjectionTarget();

    LocalVariableLookup cleanLocalsTable();

    LocalVariableLookup dirtyLocalsTable();

    @Nullable
    MethodQualifier getTargetMethodQualifier();

    List<AbstractInsnNode> findInjectionTargetInsns(@Nullable TargetPair target);

    /**
     * Uncached variant of {@link #findInjectionTargetInsns(TargetPair)}
     */
    List<AbstractInsnNode> computeInjectionTargetInsns(@Nullable TargetPair target);

    @Nullable
    Pair<ClassNode, List<MethodNode>> findInjectionTargetCandidates(ClassLookup lookup, boolean ignoreDesc);

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

    boolean failsDirtyInjectionCheck();

    boolean isNotRequired();

    boolean hasValidSlice(TargetPair target);
}
