package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @ModifyArgs(method = "computeFogColor", at = @At(value = "INVOKE", target = "Lorg/joml/Vector4f;set(FFFF)Lorg/joml/Vector4f;"))
    public void darkenFogColor(Args args) {
        if (Minecraft.getInstance().level != null) {
            args.set(0, (float) args.get(0) * 0.5F);
        }
    }
}
