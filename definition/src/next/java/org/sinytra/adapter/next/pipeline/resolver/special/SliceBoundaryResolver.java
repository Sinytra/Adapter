package org.sinytra.adapter.next.pipeline.resolver.special;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.ann.SliceData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.refmap.IMixinContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.next.pipeline.config.Configuration.Keys.SLICE;

public class SliceBoundaryResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinData mixin, MixinContext context, Recipe recipe) {
        MethodContext.TargetPair dirtyTarget = recipe.getDirtyTarget();

        SliceData slice = recipe.clean().getProperty(SLICE).orElse(null);
        if (slice == null || passesSliceCheck(slice, context, dirtyTarget))
            return ResolutionResult.pass();

        SliceData fixedSlice = fixSliceData(slice, dirtyTarget.methodNode());
        if (fixedSlice == null)
            return ResolutionResult.pass();

        Configuration patch = MutableConfiguration.create()
            .setProperty(SLICE, fixedSlice);

        return ResolutionResult.success(patch);
    }

    @Nullable
    private static SliceData fixSliceData(SliceData slice, MethodNode dirtyMethod) {
        AtData from = slice.from();
        if (from != null) {
            from = fixSlideInjectionPoint(from, dirtyMethod).orElse(null);
            if (from == null) return null;
        }

        AtData to = slice.to();
        if (to != null) {
            to = fixSlideInjectionPoint(to, dirtyMethod).orElse(null);
            if (to == null) return null;
        }

        return new SliceData(from, to);
    }

    private static Optional<AtData> fixSlideInjectionPoint(AtData boundary, MethodNode dirtyMethod) {
        // Require only INVOKE values
        if (!AT_VAL_INVOKE.equals(boundary.getValue()))
            return Optional.empty();

        // Find original target invocation qualifier
        MethodQualifier target = boundary.getTarget().flatMap(MethodQualifier::create).orElse(null);
        if (target == null) return Optional.empty();

        // Find method invocations with a matching name
        List<MethodInsnNode> candidates = new ArrayList<>();
        for (AbstractInsnNode insn : dirtyMethod.instructions) {
            if (insn instanceof MethodInsnNode minsn && minsn.name.equals(target.name())) {
                candidates.add(minsn);
            }
        }
        if (candidates.size() != 1) return Optional.empty();

        MethodInsnNode insn = candidates.getFirst();
        return Optional.of(boundary.withTarget(insn));
    }

    private static boolean passesSliceCheck(SliceData slice, MixinContext context, MethodContext.TargetPair dirtyTarget) {
        Target mixinTarget = MockMixinRuntime.createMixinTarget(dirtyTarget);
        IMixinContext mixinContext = MockMixinRuntime.forClass(context.classNode().name, dirtyTarget.classNode().name, context.patchContext().environment());
        ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, context.methodNode());
        MethodSlice methodSlice = MethodSlice.parse(sliceContext, slice.toAnnotationNode());
        InsnList insns = methodSlice.getSlice(mixinTarget);

        return insns.size() != dirtyTarget.methodNode().instructions.size();
    }
}
