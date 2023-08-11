package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

import static dev.su5ed.sinytra.adapter.patch.PatchImpl.MIXINPATCH;

public record ModifyInjectionPoint(String value, String target) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyInjectionPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("value").forGetter(ModifyInjectionPoint::value),
        Codec.STRING.fieldOf("target").forGetter(ModifyInjectionPoint::target)
    ).apply(instance, ModifyInjectionPoint::new));

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        if (this.value != null) {
            ((AnnotationValueHandle<String>) Objects.requireNonNull(annotationValues.get("value"), "Missing value handle")).set(this.value);
        }
        AnnotationValueHandle<String> handle = (AnnotationValueHandle<String>) Objects.requireNonNull(annotationValues.get("target"), "Missing target handle, did you specify the target descriptor?");
        LOGGER.info(MIXINPATCH, "Changing mixin method target {}.{} to {}", classNode.name, methodNode.name, this.target);
        handle.set(this.target);
        return true;
    }
}
