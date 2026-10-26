package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityItemMixin {
    @Unique
    private final LivingEntity entity = (LivingEntity) (Object) this;

    @ModifyExpressionValue(method = "updatingUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean cancelItemUsing(boolean original) {
        return original && !this.entity.isShiftKeyDown();
    }

    @ModifyExpressionValue(method = "updatingUsingItem()V", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;canContinueUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean cancelItemUsingExpected(boolean original) {
        return original && !this.entity.isShiftKeyDown();
    }
}
