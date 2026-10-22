package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class GuiMixin {
    @WrapOperation(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void skipContextualBar(ContextualBarRenderer instance, GuiGraphicsExtractor graphics, DeltaTracker tracker, Operation<Void> original) {
        original.call(instance, graphics, tracker);
    }

    @WrapOperation(
        method = "extractContextualInfoBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void skipContextualBarExpected(ContextualBarRenderer instance, GuiGraphicsExtractor graphics, DeltaTracker tracker, Operation<Void> original) {
        original.call(instance, graphics, tracker);
    }

    @WrapOperation(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void skipContextualBarBackground(ContextualBarRenderer instance, GuiGraphicsExtractor graphics, DeltaTracker tracker, Operation<Void> original) {
        original.call(instance, graphics, tracker);
    }

    @WrapOperation(
        method = "extractContextualInfoBarBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void skipContextualBarBackgroundExpected(ContextualBarRenderer instance, GuiGraphicsExtractor graphics, DeltaTracker tracker, Operation<Void> original) {
        original.call(instance, graphics, tracker);
    }
}
