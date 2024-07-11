package org.sinytra.adapter.test.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(EffectRenderingInventoryScreen.class)
public class EffectRenderingInventoryScreenMixin {
    @Inject(method = "renderEffects", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;"))
    private void onCollect(GuiGraphics p_281945_, int p_282601_, int p_282335_) {
        // Noop
    }

    @Inject(method = "renderEffects", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private void onCollectExpected(GuiGraphics p_281945_, int p_282601_, int p_282335_) {
        // Noop
    }
}
