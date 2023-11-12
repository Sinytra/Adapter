package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.List;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

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
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        LOGGER.info(MIXINPATCH, "Redirecting mixin {}.{} to {}", classNode.name, methodNode.name, this.replacementMethods);
        AnnotationHandle annotation = methodContext.methodAnnotation();
        if (annotation.matchesDesc(Patch.OVERWRITE)) {
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
        return Result.APPLY;
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