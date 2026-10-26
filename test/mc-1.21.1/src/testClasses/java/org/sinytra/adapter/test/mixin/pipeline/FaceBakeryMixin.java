package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FaceBakery.class)
public class FaceBakeryMixin {
    @Redirect(
        method = "bakeQuad",
        at = @At(value = "NEW", target = "([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Z)Lnet/minecraft/client/renderer/block/model/BakedQuad;")
    )
    private BakedQuad createQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade) {
        return null;
    }

    @Redirect(
        method = "bakeQuad",
        at = @At(value = "NEW", target = "([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZ)Lnet/minecraft/client/renderer/block/model/BakedQuad;")
    )
    private BakedQuad createQuadExpected(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, boolean adapter_injected_5) {
        return null;
    }
}
