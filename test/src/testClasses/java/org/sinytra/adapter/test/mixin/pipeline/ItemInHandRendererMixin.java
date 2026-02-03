package org.sinytra.adapter.test.mixin.pipeline;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.sinytra.adapter.runtime.inject.ModifyInstanceofValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Redirect(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z",
            ordinal = 1
        )
    )
    private boolean renderFirstPersonItem(ItemStack instance, Item item) {
        return instance.getItem() instanceof CrossbowItem;
    }

    @ModifyExpressionValue(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z",
            ordinal = 1
        )
    )
    private boolean renderFirstPersonItemMEV(boolean original) {
        return original;
    }

    @ModifyInstanceofValue(
        method = "renderArmWithItem",
        at = @At(
            value = "sinytra:INSTANCEOF",
            target = "net/minecraft/world/item/CrossbowItem"
        )
    )
    private boolean renderFirstPersonItemMEVExpected(boolean original) {
        return original;
    }
}
