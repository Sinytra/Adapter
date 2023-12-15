package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public class DisableMixin implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DisableMixin INSTANCE = new DisableMixin();
    public static final Codec<MethodTransform> CODEC = Codec.unit(INSTANCE);

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        LOGGER.debug(MIXINPATCH, "Removing mixin method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
        context.postApply(() -> classNode.methods.remove(methodNode));
        return Result.APPLY;
    }
}
