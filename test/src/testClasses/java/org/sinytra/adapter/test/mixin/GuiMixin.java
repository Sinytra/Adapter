package org.sinytra.adapter.test.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Gui.class)
public class GuiMixin {
    // https://github.com/juancarloscp52/BedrockIfy/blob/c4bc4f425adffaab1a7bdf4c8740f12281e94668/src/main/java/me/juancarloscp52/bedrockify/mixin/client/features/screenSafeArea/InGameHudMixin.java#L122
    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), index = 2)
    public int modifyTextureStatusBar(int y) {
        return y;
    }

    @ModifyArg(method = "renderAirLevel(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), index = 2)
    public int modifyTextureStatusBarExpected(int y) {
        return y;
    }

    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V"), index = 2)
    private int modifyTextureStatusBarsArmor(int i) {
        return i;
    }

    @ModifyArg(method = "renderArmorLevel(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V"), index = 2)
    private int modifyTextureStatusBarsArmorExpected(int i) {
        return i;
    }

    @ModifyArg(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V"), index = 2)
    private int modifyTextureStatusBarsFood(int y) {
        return y;
    }

    @ModifyArg(method = "renderFoodLevel(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V"), index = 2)
    private int modifyTextureStatusBarsFoodExpected(int y) {
        return y;
    }
}
