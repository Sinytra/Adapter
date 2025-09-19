package org.sinytra.adapter.patch.transformer.operation.unit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.MethodUpgrader;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public record ModifyInjectionTarget(List<String> replacementMethods, Action action) implements MethodTransform {
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
        AnnotationHandle annotation = methodContext.methodAnnotation();

        methodContext.recordAudit(this, "Change mixin target to %s", this.replacementMethods);
        if (annotation.matchesDesc(MixinConstants.OVERWRITE)) {
            if (this.replacementMethods.size() > 1) {
                throw new IllegalStateException("Cannot determine replacement @Overwrite method name, multiple specified: " + this.replacementMethods);
            }
            String replacement = this.replacementMethods.getFirst();
            MethodQualifier.create(replacement)
                .map(MethodQualifier::name)
                .ifPresent(str -> methodNode.name = str);
        } else {
            annotation.<List<String>>getValue("method").ifPresentOrElse(
                handle -> {
                    List<String> original = handle.get();
                    this.action.handler.apply(handle, methodContext.matchingTargets(), this.replacementMethods);
                    if (original.size() == 1 && handle.get().size() == 1) {
                        // TODO Remove side effect
                        MethodUpgrader.upgradeMethod(methodNode, methodContext, original.getFirst(), handle.get().getFirst());
                    }
                },
                () -> annotation.appendValue("method", this.replacementMethods)
            );
        }

        if (methodContext.capturesLocals()) {
            MethodUpgrader.upgradeCapturedLocals(methodNode, methodContext);
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