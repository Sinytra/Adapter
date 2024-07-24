package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DynFixSliceBoundary implements DynamicFixer<DynFixSliceBoundary.Data> {
    public record Data(AnnotationHandle slice, List<AnnotationHandle> slices) {}

    @Nullable
    @Override
    public DynFixSliceBoundary.Data prepare(MethodContext methodContext) {
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        if (dirtyTarget == null) {
            return null;
        }
        AnnotationHandle sliceAnnotation = methodContext.methodAnnotation().getNested("slice").orElse(null);
        if (sliceAnnotation == null) {
            return null;
        }
        if (passesSliceCheck(sliceAnnotation, methodContext)) {
            return null;
        }
        List<AnnotationHandle> slices = Stream.of("from", "to").flatMap(s -> sliceAnnotation.getNested(s).stream()).toList();
        return new Data(sliceAnnotation, slices);
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        Target mixinTarget = MockMixinRuntime.createMixinTarget(dirtyTarget);
        AnnotationHandle slice = data.slice();

        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, dirtyTarget.classNode().name, methodContext.patchContext().environment());
        ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, methodNode);
        MethodSlice methodSlice = MethodSlice.parse(sliceContext, slice.unwrap());
        InsnList insns = methodSlice.getSlice(mixinTarget);

        if (insns.size() != dirtyTarget.methodNode().instructions.size()) {
            return null;
        }

        return FixResult.of(data.slices().stream()
            .reduce(Patch.Result.PASS, (a, b) -> a.or(fixSlideInjectionPoint(b, dirtyTarget.methodNode())), Patch.Result::or), PatchAuditTrail.Match.FULL);
    }

    private static Patch.Result fixSlideInjectionPoint(AnnotationHandle annotation, MethodNode dirtyMethod) {
        // Require only INVOKE values
        if (!annotation.<String>getValue("value").map(AnnotationValueHandle::get).map("INVOKE"::equals).orElse(false)) {
            return Patch.Result.PASS;
        }
        // Find original target invocation qualifier
        AnnotationValueHandle<String> targetHandle = annotation.<String>getValue("target").orElse(null);
        if (targetHandle == null) {
            return Patch.Result.PASS;
        }
        MethodQualifier target = MethodQualifier.create(targetHandle.get()).orElse(null);
        if (target == null) {
            return Patch.Result.PASS;
        }
        // Find method invocations with a matching name
        List<MethodInsnNode> candidates = new ArrayList<>();
        for (AbstractInsnNode insn : dirtyMethod.instructions) {
            if (insn instanceof MethodInsnNode minsn && minsn.name.equals(target.name())) {
                candidates.add(minsn);
            }
        }

        if (candidates.size() != 1) {
            return Patch.Result.PASS;
        }

        MethodInsnNode insn = candidates.getFirst();
        String qualifier = Type.getObjectType(insn.owner) + insn.name + insn.desc;
        targetHandle.set(qualifier);
        return Patch.Result.APPLY;
    }

    private static boolean passesSliceCheck(AnnotationHandle slice, MethodContext methodContext) {
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        Target mixinTarget = MockMixinRuntime.createMixinTarget(dirtyTarget);

        IMixinContext mixinContext = MockMixinRuntime.forClass(methodContext.getMixinClass().name, dirtyTarget.classNode().name, methodContext.patchContext().environment());
        ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, methodContext.getMixinMethod());
        MethodSlice methodSlice = MethodSlice.parse(sliceContext, slice.unwrap());
        InsnList insns = methodSlice.getSlice(mixinTarget);

        return insns.size() != dirtyTarget.methodNode().instructions.size();
    }
}
