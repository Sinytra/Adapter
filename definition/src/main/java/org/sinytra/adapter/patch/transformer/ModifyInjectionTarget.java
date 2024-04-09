package org.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.MethodUpgrader;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyInjectionTarget(List<String> replacementMethods, Action action) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyInjectionTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().fieldOf("replacementMethods").forGetter(ModifyInjectionTarget::replacementMethods),
        Action.CODEC.optionalFieldOf("action", Action.OVERWRITE).forGetter(ModifyInjectionTarget::action)
    ).apply(instance, ModifyInjectionTarget::new));

    public ModifyInjectionTarget(List<String> replacementMethods) {
        this(replacementMethods, Action.OVERWRITE);
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        LOGGER.info(MIXINPATCH, "Redirecting mixin {}.{} to {}", classNode.name, methodNode.name, this.replacementMethods);
        AnnotationHandle annotation = methodContext.methodAnnotation();

        if (annotation.matchesDesc(MixinConstants.OVERWRITE)) {
            if (this.replacementMethods.size() > 1) {
                throw new IllegalStateException("Cannot determine replacement @Overwrite method name, multiple specified: " + this.replacementMethods);
            }
            String replacement = this.replacementMethods.get(0);
            MethodQualifier.create(replacement)
                .map(MethodQualifier::name)
                .ifPresent(str -> methodNode.name = str);
        } else {
            annotation.<List<String>>getValue("method").ifPresentOrElse(
                handle -> this.action.handler.apply(handle, methodContext.matchingTargets(), this.replacementMethods),
                () -> annotation.appendValue("method", this.replacementMethods)
            );
        }

        if (methodContext.capturesLocals()) {
            MethodUpgrader.upgradeCapturedLocals(classNode, methodNode, methodContext);
        }

        return Patch.Result.APPLY;
    }

    public enum Action {
        ADD((handle, targets, replacements) -> handle.get().addAll(replacements)),
        REPLACE((handle, targets, replacements) -> {
            List<String> value = handle.get();
            value.removeAll(targets);
            value.addAll(replacements);
        }),
        OVERWRITE((handle, targets, replacements) -> handle.set(replacements));

        private static final Codec<Action> CODEC = Codec.STRING.xmap(Action::valueOf, Action::name);
        private final TargetHandler handler;

        Action(TargetHandler handler) {
            this.handler = handler;
        }
    }

    public interface TargetHandler {
        void apply(AnnotationValueHandle<List<String>> handle, List<String> targets, List<String> replacements);
    }
}