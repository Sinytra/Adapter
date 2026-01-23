package org.sinytra.adapter.next.pipeline.resolver.target;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.next.pipeline.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.api.TargetPair;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Comparator;
import java.util.List;

public class TargetMethodSubResolvers {
    private static final String DEPRECATED = "Ljava/lang/Deprecated;";

    /**
     * Handle cases where the target method's parameters have changed
     * <p>
     * For example:
     * CLEAN: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V</code>
     * <br>
     * DIRTY: <code>Lnet/minecraft/server/level/ServerEntity;sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V</code>
     */
    public static final SubResolver CHANGED_METHOD_PARAMS = (MixinContext context, Recipe recipe) -> {
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();

        Pair<ClassNode, List<MethodNode>> candidates = context.methods().findOwnMethodsByName(context.dirtyLookup(), cleanQualifier);
        if (candidates == null) return null;

        // Find single matching candidate
        Configuration resolved = resolveReplacementCandidate(context, recipe, candidates.getSecond());
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
        MethodQualifier cleanQualifier = recipe.clean().getTargetMethod();

        TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), cleanQualifier);
        if (target == null) return null;

        for (AbstractInsnNode insn : target.methodNode().instructions) {
            // Find lambda invocations and search for target insns inside the lambda
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length > 1 && indy.bsmArgs[1] instanceof Handle handle) {
                TargetPair lambda = MethodQualifier.create(handle.getName())
                    .map(q -> context.methods().findOwnMethodPair(context.dirtyLookup(), q))
                    .orElse(null);
                if (lambda == null) return null;

                if (!context.methods().findInjectionTargetInsns(lambda).isEmpty()) {
                    return MutableConfiguration.create()
                        .setTargetMethod(lambda.methodNode());
                }
            }
        }

        return null;
    };

    @Nullable
    private static Configuration resolveReplacementCandidate(MixinContext context, Recipe recipe, List<MethodNode> methods) {
        if (methods.size() == 1) {
            return MutableConfiguration.create()
                .setTargetMethod(methods.getFirst());
        }

        Resolver resolver = recipe.resolvers().get(InjectionPointResolver.class);

        List<Pair<MethodNode, Configuration>> valid = methods.stream()
            .sorted(Comparator.<MethodNode, String>comparing(m -> m.desc).reversed())
            .<Pair<MethodNode, Configuration>>flatMap(m -> {
                Configuration dirtyCopy = recipe.dirty().copy().setTargetMethod(m);
                return resolver.resolve(context, recipe.withDirtyConfig(dirtyCopy))
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

        return null;
    }

    public static boolean isDirtyDeprecatedMethod(MixinContext context, MethodNode dirty) {
        TargetPair pair = context.methods().findOwnMethodPair(context.cleanLookup(), MethodQualifier.create(dirty));
        return (pair == null || !AdapterUtil.hasAnnotation(pair.methodNode().visibleAnnotations, DEPRECATED))
            && !AdapterUtil.hasAnnotation(dirty.visibleAnnotations, DEPRECATED);
    }
}
