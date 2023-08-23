package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Map;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ChangeModifiedVariableIndex(int start, int offset) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ChangeModifiedVariableIndex> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("start").forGetter(ChangeModifiedVariableIndex::start),
        Codec.INT.fieldOf("offset").forGetter(ChangeModifiedVariableIndex::offset)
    ).apply(instance, ChangeModifiedVariableIndex::new));

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }
    
    @Override
    public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        return PatchInstance.<Integer>findAnnotationValue(annotation.values, "index")
            .filter(index -> index.get() > -1)
            .map(handle -> {
                int index = handle.get();
                int newIndex = index >= this.start ? index + this.offset : index;
                LOGGER.info(MIXINPATCH, "Updating variable index of variable modifier method {}.{} to {}", classNode.name, methodNode.name, newIndex);
                handle.set(newIndex);
                return true;
            })
            .orElse(false);
    }
}
