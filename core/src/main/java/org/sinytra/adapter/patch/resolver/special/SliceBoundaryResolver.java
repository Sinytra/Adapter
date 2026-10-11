package org.sinytra.adapter.patch.resolver.special;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ann.SliceData;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.patch.config.key.MixinKeys.SLICE;
import static org.sinytra.adapter.patch.config.key.MixinKeys.SLICES;

public class SliceBoundaryResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) return ResolutionResult.pass();

        SliceData slice = recipe.clean().getProperty(SLICE).orElse(null);
        if (slice != null) {
            if (passesSliceCheck(slice, context, dirtyTarget))
                return ResolutionResult.pass();

            SliceData fixedSlice = fixSliceData(slice, dirtyTarget.methodNode());
            if (fixedSlice == null)
                return ResolutionResult.pass();

            return ResolutionResult.success(MutableConfiguration.create().setProperty(SLICE, fixedSlice));
        }

        List<SliceData> slices = recipe.clean().getProperty(SLICES).orElse(null);
        if (slices == null || slices.isEmpty())
            return ResolutionResult.pass();

        List<SliceData> fixedSlices = new ArrayList<>();
        boolean changed = false;
        for (SliceData s : slices) {
            if (passesSliceCheck(s, context, dirtyTarget)) {
                fixedSlices.add(s);
                continue;
            }
            SliceData fixedSlice = fixSliceData(s, dirtyTarget.methodNode());
            if (fixedSlice == null)
                return ResolutionResult.pass();
            fixedSlices.add(fixedSlice);
            changed = true;
        }
        if (!changed)
            return ResolutionResult.pass();

        return ResolutionResult.success(MutableConfiguration.create().setProperty(SLICES, fixedSlices));
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

        return new SliceData(slice.id(), from, to);
    }

    private static Optional<AtData> fixSlideInjectionPoint(AtData boundary, MethodNode dirtyMethod) {
        // Require only INVOKE values
        if (!AT_VAL_INVOKE.equals(boundary.getValue()))
            return Optional.empty();

        // Find original target invocation qualifier
        MethodQualifier target = boundary.getTargetQualifier().orElse(null);
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

    private static boolean passesSliceCheck(SliceData slice, MixinContext context, TargetPair dirtyTarget) {
        InsnList insns = context.methods().computeSlice(dirtyTarget, slice.toAnnotationNode());
        return insns.size() != dirtyTarget.methodNode().instructions.size();
    }
}
