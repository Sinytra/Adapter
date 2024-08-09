package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collection;
import java.util.Iterator;

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

    // https://github.com/MoriyaShiine/hearty-meals/blob/3b3042e3c2f774987896273623f5f63055401471/src/main/java/moriyashiine/heartymeals/mixin/client/InGameHudMixin.java#L103
    @Inject(
        method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;getEffect()Lnet/minecraft/core/Holder;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void cozyBackground(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci,
                                Collection collection, MobEffectTextureManager statusEffectSpriteManager, Iterator var8, MobEffectInstance statusEffectInstance, Holder registryEntry
    ) {
        // Use only a single captured local
        boolean something = registryEntry == null;
    }

    @Inject(
        method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;getEffect()Lnet/minecraft/core/Holder;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void cozyBackgroundExpected(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci, @Local Holder registryEntry) {
        // Use only a single captured local
        boolean something = registryEntry == null;
    }

    // https://github.com/SkyblockerMod/Skyblocker/blob/fa67224da0cdcd2325b24f41732e820ff7ef04e8/src/main/java/de/hysky/skyblocker/mixins/InGameHudMixin.java#L97
    @ModifyExpressionValue(
        method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I")
    )
    private int moveHealthDown(int original) {
        return original;
    }

    @ModifyExpressionValue(
        method = "renderHealthLevel(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I")
    )
    private int moveHealthDownExpected(int original) {
        return original;
    }
}
