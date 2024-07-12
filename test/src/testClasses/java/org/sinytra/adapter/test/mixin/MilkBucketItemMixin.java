package org.sinytra.adapter.test.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public class MilkBucketItemMixin {
    // https://github.com/Patbox/brewery/blob/ffdaf1f5298e7ef4f82447546d2afb68286b6acd/src/main/java/eu/pb4/brewery/mixin/MilkBucketItemMixin.java#L30
    @Inject(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z", shift = At.Shift.BEFORE))
    private void onClearStatusEffect(ItemStack stack, Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        // Noop
    }

    @Inject(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeEffectsCuredBy(Lnet/neoforged/neoforge/common/EffectCure;)Z"))
    private void onClearStatusEffectExpected(ItemStack stack, Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        // Noop
    }
}
