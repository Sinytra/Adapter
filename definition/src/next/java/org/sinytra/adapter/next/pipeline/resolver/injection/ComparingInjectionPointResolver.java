package org.sinytra.adapter.next.pipeline.resolver.injection;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ConstantData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.next.env.param.Parameter;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Configuration.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.processor.wrapop.WrapOpSurgeon;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.MethodLabelComparator;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.PROPERTY_ORDINAL;

public abstract class ComparingInjectionPointResolver implements SubResolver {

    @Nullable
    protected AbstractInsnNode prepare(MixinContext context, Recipe recipe) {
        MethodContext.TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) return null;

        MethodContext.TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) return null;

        return context.methods().findInjectionTargetInsn(cleanTarget);
    }

    public static class Inject extends ComparingInjectionPointResolver {
        @Nullable
        @Override
        public Configuration resolve(MixinData mixin, MixinContext context, Recipe recipe) {
            MethodContext.TargetPair cleanTarget = recipe.getCleanTarget();
            if (cleanTarget == null) return null;

            AbstractInsnNode cleanInsn = prepare(context, recipe);
            MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInsn, context.legacy());
            if (comparisonResult == null) return null;

            List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

            ClassNode cleanTargetClass = cleanTarget.classNode();
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
                            .setAtData(new AtData(AT_VAL_INVOKE, minsn));
                    }
                }
            }
            return null;
        }

        private static Configuration attemptExtractMixin(MethodInsnNode minsn, MixinContext context, Configuration clean) {
            // Looks like some code was moved into a static method outside this class
            // Attempt extracting mixin
            MethodContext.TargetPair target = context.methods().findMethodPair(context.dirtyLookup(), MethodQualifier.create(minsn));
            if (target == null) return null;

//            MethodUpgrader.adjustInjectorOrdinalForNewMethod(minsn, context.legacy()); FIXME
            List<AbstractInsnNode> newTargetInsns = context.methods().findInjectionTargetInsns(target, true);
            if (newTargetInsns.isEmpty()) return null;

            return MutableConfiguration.create()
                .setTargetClass(target.classNode().name)
                .setTargetMethod(minsn)
                .setAtData(clean.getAtData().withOrdinal(null)) // TODO Find new ordinal
                .setProperty(Configuration.SpecialKeys.EXTRACT_TARGET, minsn);
        }
    }

    public static class WrapOperation extends ComparingInjectionPointResolver {
        @Nullable
        @Override
        public Configuration resolve(MixinData mixin, MixinContext context, Recipe recipe) {
            MethodContext.TargetPair dirtyTarget = recipe.getDirtyTarget();
            if (dirtyTarget == null) return null;

            AbstractInsnNode cleanInsn = prepare(context, recipe);
            if (!(cleanInsn instanceof MethodInsnNode minsn)) return null;

            MethodLabelComparator.ComparisonResult comparisonResult = MethodLabelComparator.findPatchedLabels(cleanInsn, context.legacy());
            if (comparisonResult == null) return null;

            List<List<AbstractInsnNode>> hunkLabels = comparisonResult.patchedLabels();

            return handleWrapOperationToInstanceOf(recipe, minsn, comparisonResult.cleanLabel(), hunkLabels, context)
                .or(() -> handleWrapOperationAdaptedTarget(hunkLabels))
                .or(() -> handleWrapOperationNewInjectionPoint(minsn, comparisonResult.cleanLabel(), hunkLabels))
                .or(() -> handleTargetModification(hunkLabels, dirtyTarget, context))
                .orElse(null);
        }

        private static Optional<Configuration> handleWrapOperationToInstanceOf(Recipe recipe, MethodInsnNode cleanInjectionInsn, List<AbstractInsnNode> cleanLabel, List<List<AbstractInsnNode>> hunkLabels, MixinContext context) {
            if (hunkLabels.size() != 1 || !(cleanLabel.getLast() instanceof JumpInsnNode) || cleanLabel.stream().anyMatch(i -> i instanceof TypeInsnNode))
                return Optional.empty();

            List<AbstractInsnNode> dirtyLabel = hunkLabels.getFirst();
            if (!(dirtyLabel.getLast() instanceof JumpInsnNode)) return Optional.empty();

            List<TypeInsnNode> instanceOfCalls = dirtyLabel.stream()
                .filter(TypeInsnNode.class::isInstance)
                .map(TypeInsnNode.class::cast)
                .toList();
            if (instanceOfCalls.size() != 1) return Optional.empty();
            TypeInsnNode instanceOfCall = instanceOfCalls.getFirst();

            MethodNode methodNode = context.methodNode();
            LocalVariableLookup mixinLocals = new LocalVariableLookup(methodNode);
            LocalVariableNode instanceLocal = mixinLocals.getByParameterOrdinal(0);

            Configuration clean = recipe.clean();
            List<Type> inheritedParams = clean.getParameters().getTypes(ParamGroup.METHOD_PARAMS);
            Multimap<Integer, VarInsnNode> usedVars = WrapOpSurgeon.getUsedVars(mixinLocals, inheritedParams, context);

            MutableConfiguration config = recipe.dirty().subConfig()
                .removeProperty(Keys.TARGET_AT)
                .setProperty(Keys.TARGET_CONSTANT, ConstantData.classValue(Type.getObjectType(instanceOfCall.desc)))
                .inheritParameters()
                .inheritReturnType();
            MethodParameters parameters = config.getParameters();
            Parameter newInstanceParam = Parameter.simple(MixinConstants.OBJECT_TYPE);
            parameters.set(ParamGroup.METHOD_PARAMS, List.of(newInstanceParam));

            if (usedVars.containsKey(instanceLocal.index)) {
                MethodContext methodContext = context.legacy();
                List<AbstractInsnNode> originalCallArgs = MethodCallAnalyzer.getMethodCallSrcInsns(methodContext.findCleanInjectionTarget().methodNode(), cleanInjectionInsn);
                int cleanOrdinal = methodContext.cleanLocalsTable().getTypedOrdinal(methodContext.cleanLocalsTable().getByIndex(((VarInsnNode) originalCallArgs.getFirst()).var)).orElse(-1);
                if (cleanOrdinal == -1) return Optional.empty();

                LocalVariableNode dirtyLocal = methodContext.dirtyLocalsTable().getByTypedOrdinal(Type.getType(instanceLocal.desc), cleanOrdinal).orElse(null);
                if (dirtyLocal == null) return Optional.empty();

                Parameter replacementInstanceParam = Parameter.builder(dirtyLocal.desc)
                    .annotate(MixinConstants.LOCAL, b -> b
                        .put(PROPERTY_ORDINAL, cleanOrdinal))
                    .build();
                parameters.add(ParamGroup.LOCALS, replacementInstanceParam);
                // TODO exclude original.call(...) insns from being mapped
                parameters.mapParameter(newInstanceParam, replacementInstanceParam);
            }

            // TODO Find a way to get rid of this ugly hack
            // Use "state.getBlock()" as the new Block parameter
            if (instanceOfCall.getPrevious() instanceof MethodInsnNode m) {
                String loadedType = Type.getReturnType(m.desc).getDescriptor();
                for (int paramVar : usedVars.keySet()) {
                    if (paramVar == instanceLocal.index) {
                        continue;
                    }

                    LocalVariableNode node = mixinLocals.getByIndex(paramVar);
                    if (!loadedType.equals(node.desc)) return Optional.empty();

                    int paramOrdinal = mixinLocals.getParameterOrdinal(node);
                    Parameter oldParam = clean.getParameters().get(ParamGroup.METHOD_PARAMS).get(paramOrdinal);
                    parameters.mapParameter(oldParam, newInstanceParam);
                }
            }

            return Optional.of(config);
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
                .setAtData(new AtData(AT_VAL_INVOKE, insn)));
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
                .setAtData(new AtData(AT_VAL_INVOKE, dirtyMinsn)));
        }

        private static Optional<Configuration> handleTargetModification(List<List<AbstractInsnNode>> hunkLabels, MethodContext.TargetPair dirtyTarget, MixinContext context) {
            ClassNode dirtyClass = dirtyTarget.classNode();
            return hunkLabels.stream()
                .flatMap(Collection::stream)
                .filter(insn -> insn instanceof MethodInsnNode minsn && minsn.owner.equals(dirtyClass.name))
                .map(MethodInsnNode.class::cast)
                .filter(minsn -> {
                    MethodContext.TargetPair pair = context.methods().findMethodPair(context.dirtyLookup(), MethodQualifier.create(minsn));
                    return pair != null && context.methods().hasInjectionTargetInsns(pair);
                })
                .<Configuration>map(minsn -> MutableConfiguration.create()
                    .setAtData(new AtData(AT_VAL_INVOKE, minsn)))
                .findFirst();
        }
    }
}
