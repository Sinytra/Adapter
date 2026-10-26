package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.core.dispenser.DispenseItemBehavior$10")
public class DispenseItemBehaviorMixin {
    @Inject(method = "execute", at = @At("HEAD"))
    private void dispense(BlockSource source, ItemStack dispensed, CallbackInfoReturnable<ItemStack> cir) {
    }
}
