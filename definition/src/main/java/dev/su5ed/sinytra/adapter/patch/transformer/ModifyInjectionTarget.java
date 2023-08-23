package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyInjectionTarget(List<String> replacementMethods) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyInjectionTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().fieldOf("replacementMethods").forGetter(ModifyInjectionTarget::replacementMethods)
    ).apply(instance, ModifyInjectionTarget::new));

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        LOGGER.info(MIXINPATCH, "Redirecting mixin {}.{} to {}", classNode.name, methodNode.name, this.replacementMethods);
        if (annotation.desc.equals(Patch.OVERWRITE)) {
            if (this.replacementMethods.size() > 1) {
                throw new IllegalStateException("Cannot determine replacement @Overwrite method name, multiple specified: " + this.replacementMethods);
            }
            methodNode.name = this.replacementMethods.get(0);
        } else {
            AnnotationValueHandle<List<String>> targetMethods = (AnnotationValueHandle<List<String>>) annotationValues.get("method");
            targetMethods.set(this.replacementMethods);
        }
        return true;
    }
}