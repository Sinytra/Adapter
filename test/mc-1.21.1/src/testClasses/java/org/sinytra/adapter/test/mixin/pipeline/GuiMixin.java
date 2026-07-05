package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(
        method = "renderSelectedItemName",
        at = @At("HEAD")
    )
    public void renderSelectedItemName(GuiGraphics guiGraphics, CallbackInfo ci) {

    }

    @Inject(
        method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("HEAD")
    )
    public void renderSelectedItemNameExpected(GuiGraphics guiGraphics, int adapter_injected_1, CallbackInfo ci) {

    }
}
