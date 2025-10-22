package org.sinytra.adapter.patch.transformer.operation.unit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.MethodUpgrader;

import java.util.Optional;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_SHIFT;

public record ModifyInjectionPoint(@Nullable String value, String target, boolean resetValues, boolean dontUpgrade) implements MethodTransform {
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
        methodContext.recordAudit(this, "Change injection point to %s", this.target);
        AnnotationValueHandle<String> handle = annotation.<String>getValue("target").orElse(null);
        if (handle != null) {
            String original = handle.get();
            handle.set(this.target);
            // TODO Remove side effect
            if (!this.dontUpgrade && !methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
                MethodUpgrader.upgradeMethod(methodNode, methodContext, original, this.target);
            }
        } else {
            annotation.appendValue("target", this.target);
        }
        if (this.resetValues) {
            methodContext.methodAnnotation().removeValues("slice");
            annotation.removeValues("ordinal", AT_SHIFT, "by", "opcode");
        }
        return Patch.Result.APPLY;
    }
}
