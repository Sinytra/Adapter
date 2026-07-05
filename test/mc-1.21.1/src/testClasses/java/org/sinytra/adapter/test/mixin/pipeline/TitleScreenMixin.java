package org.sinytra.adapter.test.mixin.pipeline;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
            ordinal = 0
        )
    )
    private String onRender(String string) {
        return string;
    }

    @ModifyArg(
        method = "lambda$render$13(Lnet/minecraft/client/gui/GuiGraphics;ILjava/lang/Integer;Ljava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
            ordinal = 0
        )
    )
    private String onRenderExpected(String string) {
        return string;
    }
}
