package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

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
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        return methodContext.methodAnnotation().<Integer>getValue("index")
            .filter(index -> index.get() > -1)
            .map(handle -> {
                int index = handle.get();
                int newIndex = index >= this.start ? index + this.offset : index;
                LOGGER.info(MIXINPATCH, "Updating variable index of variable modifier method {}.{} to {}", classNode.name, methodNode.name, newIndex);
                handle.set(newIndex);
                return Result.APPLY;
            })
            .orElse(Result.PASS);
    }
}
