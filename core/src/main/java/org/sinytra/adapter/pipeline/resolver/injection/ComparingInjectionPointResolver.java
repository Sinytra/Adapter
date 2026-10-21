package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.compare.MethodLabelComparator;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;

public abstract class ComparingInjectionPointResolver implements SubResolver {

    public static class Inject extends ComparingInjectionPointResolver {
        @Nullable
        @Override
        public Configuration resolve(MixinContext context, Recipe recipe) {
            Targets targets = recipe.targets();
            if (targets == null) return null;

            AbstractInsnNode cleanInsn = recipe.getCleanInjectionInsn();
            MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInsn, recipe);
            if (comparisonResult == null) return null;

            List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

            ClassNode cleanTargetClass = targets.clean().classNode();
            for (List<AbstractInsnNode> insns : hunkLabels) {
                for (AbstractInsnNode insn : insns) {
                    if (insn instanceof MethodInsnNode minsn && minsn.getOpcode() == Opcodes.INVOKESTATIC && !minsn.owner.equals(cleanTargetClass.name)) {
                        Configuration result = attemptExtractMixin(minsn, context, recipe.clean());
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }

            // If extraction fails, fall back to injecting at the nearest method call in dirty code
            for (List<AbstractInsnNode> insns : hunkLabels) {
                for (AbstractInsnNode insn : insns) {
                    if (insn instanceof MethodInsnNode minsn) {
                        return MutableConfiguration.create()
                            .setAtData(AtData.create(AT_VAL_INVOKE, minsn));
                    }
                }
            }
            return null;
        }

        private static Configuration attemptExtractMixin(MethodInsnNode minsn, MixinContext context, Configuration clean) {
            // Looks like some code was moved into a static method outside this class
            // Attempt extracting mixin
            TargetPair target = context.dirtyMethods().resolveDeclared(MethodQualifier.create(minsn));
            if (target == null) return null;

//            MethodUpgrader.adjustInjectorOrdinalForNewMethod(minsn, context.legacy()); FIXME
            List<AbstractInsnNode> newTargetInsns = context.injections().find(target, InjectionQuery.current().ignoringOrdinal());
            if (newTargetInsns.isEmpty()) return null;

            return MutableConfiguration.create()
                .setTargetClass(target.classNode().name)
                .setTargetMethod(minsn)
                .setAtData(clean.getAtData().withOrdinal(null)) // TODO Find new ordinal
                .setProperty(IntentKeys.RELOCATION, new Relocation.CalledFrom(minsn));
        }
    }

    public static class WrapOperation extends ComparingInjectionPointResolver {
        @Nullable
        @Override
        public Configuration resolve(MixinContext context, Recipe recipe) {
            TargetPair dirtyTarget = recipe.getDirtyTarget();
            if (dirtyTarget == null || recipe.getCleanTarget() == null) return null;

            AbstractInsnNode cleanInsn = recipe.getCleanInjectionInsn();
            if (!(cleanInsn instanceof MethodInsnNode minsn)) return null;

            MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInsn, recipe);
            if (comparisonResult == null) return null;

            List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

            return handleWrapOperationAdaptedTarget(hunkLabels)
                .or(() -> handleWrapOperationNewInjectionPoint(minsn, comparisonResult.cleanLabel(), hunkLabels))
                .or(() -> handleTargetModification(hunkLabels, dirtyTarget, context))
                .orElse(null);
        }

        private static Optional<Configuration> handleWrapOperationAdaptedTarget(List<List<AbstractInsnNode>> hunkLabels) {
            if (hunkLabels.size() != 1) return Optional.empty();

            List<MethodInsnNode> methodCalls = hunkLabels.getFirst().stream()
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
            if (methodCalls.size() != 1) return Optional.empty();
            MethodInsnNode insn = methodCalls.getLast();

            return Optional.of(MutableConfiguration.create()
                .setAtData(AtData.create(AT_VAL_INVOKE, insn)));
        }

        private static Optional<Configuration> handleWrapOperationNewInjectionPoint(MethodInsnNode cleanInjectionInsn, List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> hunkLabels) {
            if (hunkLabels.size() != 1)
                return Optional.empty();

            Type cleanReturnType = Type.getReturnType(cleanInjectionInsn.desc);
            List<AbstractInsnNode> dirtyLabel = hunkLabels.getFirst();
            List<String> cleanMethodCalls = cleanLabel.stream()
                .filter(i -> i instanceof MethodInsnNode m
                    && m.owner.equals(cleanInjectionInsn.owner)
                    && Type.getReturnType(m.desc).equals(cleanReturnType))
                .map(i -> ((MethodInsnNode) i).name)
                .toList();

            List<MethodInsnNode> methodCalls = dirtyLabel.stream()
                .filter(i -> i instanceof MethodInsnNode m && m.owner.equals(cleanInjectionInsn.owner)
                    && Type.getReturnType(m.desc).equals(cleanReturnType)
                    && !cleanMethodCalls.contains(m.name))
                .map(MethodInsnNode.class::cast)
                .toList();
            if (methodCalls.size() != 1) return Optional.empty();

            MethodInsnNode dirtyMinsn = methodCalls.getFirst();
            return Optional.of(MutableConfiguration.create()
                .setAtData(AtData.create(AT_VAL_INVOKE, dirtyMinsn)));
        }

        private static Optional<Configuration> handleTargetModification(List<List<AbstractInsnNode>> hunkLabels, TargetPair dirtyTarget, MixinContext context) {
            ClassNode dirtyClass = dirtyTarget.classNode();
            return hunkLabels.stream()
                .flatMap(Collection::stream)
                .filter(insn -> insn instanceof MethodInsnNode minsn && minsn.owner.equals(dirtyClass.name))
                .map(MethodInsnNode.class::cast)
                .filter(minsn -> {
                    TargetPair pair = context.dirtyMethods().resolveDeclared(MethodQualifier.create(minsn));
                    return pair != null && context.injections().matches(pair);
                })
                .<Configuration>map(minsn -> MutableConfiguration.create()
                    .setAtData(AtData.create(AT_VAL_INVOKE, minsn)))
                .findFirst();
        }
    }
}
