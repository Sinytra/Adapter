package org.sinytra.adapter.pipeline.resolver.target;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer.IndyHandle;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolver;
import org.sinytra.adapter.pipeline.resolver.SubResolver;
import org.sinytra.adapter.pipeline.resolver.injection.InjectionPointResolver;

import java.util.Comparator;
import java.util.List;

public class TargetMethodSubResolvers {
    /**
     * For example:
     * CLEAN: net/minecraft/server/MinecraftServer.lambda$reloadResources$28(Lcom/google/common/collect/ImmutableList;)Ljava/util/concurrent/CompletionStage;
     * DIRTY: net/minecraft/server/MinecraftServer.lambda$reloadResources$29(Lcom/google/common/collect/ImmutableList;)Ljava/util/concurrent/CompletionStage;
     */
    public static final SubResolver CHANGED_LAMBDA_INDEX = (MixinContext context, Recipe recipe) -> {
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();
        if (!MethodAnalyzer.isLambda(cleanQualifier)) return null;

        ClassNode targetClass = context.dirtyLookup().getClass(recipe.clean().getTargetClass()).orElse(null);
        if (targetClass == null) return null;

        List<MethodNode> candidateMethods = targetClass.methods.stream()
            .filter(m -> MethodAnalyzer.isLambda(m) && m.desc.equals(cleanQualifier.desc()))
            .toList();
        if (candidateMethods.size() != 1) return null;

        MethodNode newTarget = candidateMethods.getFirst();
        return MutableConfiguration.create()
            .setTargetMethod(newTarget);
    };

    /**
     * Handle cases where the target method's parameters have changed
     * <p>
     * For example:
     * CLEAN: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V</code>
     * <br>
     * DIRTY: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V</code>
     */
    public static final SubResolver CHANGED_METHOD_PARAMS = (MixinContext context, Recipe recipe) -> {
        Configuration overloaded = resolveOverloadedReplacement(context, recipe);
        if (overloaded != null) return overloaded;

        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();

        List<MethodNode> candidates = context.dirtyMethods().findByName(cleanQualifier);
        if (candidates.isEmpty()) return null;

        // Find single matching candidate
        Configuration resolved = resolveReplacementCandidate(context, recipe, candidates);
        if (resolved == null) return null;

        // Only apply single candidate change when the target desc has changed
        if (resolved.getTargetMethod() != null && !resolved.getTargetMethod().matches(cleanQualifier)) {
            return resolved;
        }

        return null;
    };

    /**
     * Handle cases where the target instructions have been moved into a lambda inside the target method
     */
    public static final SubResolver MOVED_INTO_LAMBDA = (MixinContext context, Recipe recipe) -> {
        TargetPair target = recipe.getNewCleanTarget();
        if (target == null) return null;

        // Find lambda invocations and search for target insns inside the lambda
        for (IndyHandle handle : MethodAnalyzer.findOwnIndyHandles(target.classNode(), target.methodNode())) {
            TargetPair lambda = new TargetPair(target.classNode(), handle.method());
            if (context.injections().matches(lambda)) {
                return MutableConfiguration.create()
                    .setTargetMethod(lambda.methodNode());
            }
        }

        return null;
    };

    // Resolve overloaded method by call for when the name doesn't match. Original method must be marked @Deprecated
    // Example: BoneMealItem#growCrop -> applyBonemeal
    @Nullable
    private static Configuration resolveOverloadedReplacement(MixinContext context, Recipe recipe) {
        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (dirtyTarget == null || !MethodAnalyzer.isDeprecated(dirtyTarget.methodNode())) return null;

        List<MethodNode> invocations = context.dataFlow(dirtyTarget).findTopLevelOwnCalls();
        for (MethodNode invocation : invocations) {
            if (context.injections().matches(new TargetPair(dirtyTarget.classNode(), invocation))) {
                return MutableConfiguration.create()
                    .setTargetMethod(invocation);
            }
        }

        return null;
    }

    @Nullable
    private static Configuration resolveReplacementCandidate(MixinContext context, Recipe recipe, List<MethodNode> methods) {
        if (methods.size() == 1) {
            return MutableConfiguration.create()
                .setTargetMethod(methods.getFirst());
        }

        Resolver resolver = recipe.resolvers().get(InjectionPointResolver.class);

        List<Pair<MethodNode, Configuration>> valid = methods.stream()
            .sorted(Comparator.<MethodNode>comparingInt(m -> Parameters.getParameterTypes(m.desc).size()).reversed())
            .<Pair<MethodNode, Configuration>>flatMap(m -> {
                Configuration dirtyCopy = recipe.dirty().copy().setTargetMethod(m);
                Resolver.ResolutionResult result = context.audited(resolver, () -> resolver.resolve(context, recipe.withDirtyConfig(dirtyCopy)));
                return result
                    .maybePatch()
                    .stream()
                    .map(c -> Pair.of(m, c.copyClean().setTargetMethod(m)));
            })
            .toList();
        if (valid.size() == 1) {
            return valid.getFirst().getSecond();
        }

        List<MethodNode> nonDeprecated = valid.stream()
            .map(Pair::getFirst)
            .filter(m -> !isDirtyDeprecatedMethod(context, m))
            .toList();
        if (nonDeprecated.size() == 1) {
            return MutableConfiguration.create()
                .setTargetMethod(nonDeprecated.getFirst());
        }

        // Best effort: Handle cases where the target method is overloaded, but the mixin does not specify the target descriptor,
        // resulting in ambigous targets. So we just pick the one with the most parameters;
        if (!valid.isEmpty()) {
            return valid.getFirst().getSecond();
        }

        return null;
    }

    public static boolean isDirtyDeprecatedMethod(MixinContext context, MethodNode dirty) {
        TargetPair pair = context.cleanMethods().resolve(MethodQualifier.create(dirty));
        return pair != null ? MethodAnalyzer.isDirtyDeprecatedMethod(pair.methodNode(), dirty) : MethodAnalyzer.isDeprecated(dirty);
    }
}
