package org.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.fixes.MethodUpgrader;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.slf4j.Logger;

import java.util.Optional;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyInjectionPoint(@Nullable String value, String target, boolean resetValues, boolean dontUpgrade) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyInjectionPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("value").forGetter(i -> Optional.ofNullable(i.value())),
        Codec.STRING.fieldOf("target").forGetter(ModifyInjectionPoint::target),
        Codec.BOOL.optionalFieldOf("resetValues", false).forGetter(ModifyInjectionPoint::resetValues),
        Codec.BOOL.optionalFieldOf("dontUpgrade", false).forGetter(ModifyInjectionPoint::dontUpgrade)
    ).apply(instance, ModifyInjectionPoint::new));

    public ModifyInjectionPoint(Optional<String> value, String target, boolean resetValues, boolean dontUpgrade) {
        this(value.orElse(null), target, resetValues, dontUpgrade);
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.injectionPointAnnotation();
        if (annotation == null) {
            // Likely an @Overwrite
            return Patch.Result.PASS;
        }
        if (this.value != null) {
            AnnotationValueHandle<String> handle = annotation.<String>getValue("value").orElseThrow(() -> new IllegalArgumentException("Missing value handle"));
            handle.set(this.value);
        }
        LOGGER.info(MIXINPATCH, "Changing mixin method target {}.{} to {}", classNode.name, methodNode.name, this.target);
        AnnotationValueHandle<String> handle = annotation.<String>getValue("target").orElse(null);
        if (handle != null) {
            String original = handle.get();
            handle.set(this.target);
            if (!this.dontUpgrade) {
                MethodUpgrader.upgradeMethod(classNode, methodNode, methodContext, original, this.target);
            }
        } else {
            annotation.appendValue("target", this.target);
        }
        if (this.resetValues) {
            annotation.removeValues("ordinal", "shift", "by");
        }
        return Patch.Result.APPLY;
    }
}
