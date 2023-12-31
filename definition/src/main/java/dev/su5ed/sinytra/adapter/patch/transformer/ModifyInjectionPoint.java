package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Optional;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyInjectionPoint(@Nullable String value, String target, boolean resetValues) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyInjectionPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("value").forGetter(i -> Optional.ofNullable(i.value())),
        Codec.STRING.fieldOf("target").forGetter(ModifyInjectionPoint::target),
        Codec.BOOL.optionalFieldOf("resetValues", false).forGetter(ModifyInjectionPoint::resetValues)
    ).apply(instance, ModifyInjectionPoint::new));

    public ModifyInjectionPoint(Optional<String> value, String target, boolean resetValues) {
        this(value.orElse(null), target, resetValues);
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.injectionPointAnnotationOrThrow();
        if (this.value != null) {
            AnnotationValueHandle<String> handle = annotation.<String>getValue("value").orElseThrow(() -> new IllegalArgumentException("Missing value handle"));
            handle.set(this.value);
        }
        LOGGER.info(MIXINPATCH, "Changing mixin method target {}.{} to {}", classNode.name, methodNode.name, this.target);
        AnnotationValueHandle<String> handle = annotation.<String>getValue("target").orElse(null);
        if (handle != null) {
            String original = handle.get();
            handle.set(this.target);
            if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_ARGS)) {
                ModifyArgsOffsetTransformer.handleModifiedDesc(methodNode, original, this.target);
            }
        } else {
            annotation.appendValue("target", this.target);
        }
        if (this.resetValues) {
            annotation.removeValues("ordinal", "shift", "by");
        }
        return Result.APPLY;
    }
}
