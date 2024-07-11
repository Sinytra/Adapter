package org.sinytra.adapter.test.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    // https://github.com/Sinytra/Connector/issues/1041
    @Redirect(method = "renderGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;armorEntityGlint()Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType getArmorEntityGlint() {
        return null;
    }

    @Redirect(method = "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;armorEntityGlint()Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType getArmorEntityGlintExpected() {
        return null;
    }
}
