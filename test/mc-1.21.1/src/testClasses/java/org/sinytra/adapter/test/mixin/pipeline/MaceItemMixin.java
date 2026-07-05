package org.sinytra.adapter.test.mixin.pipeline;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaceItem.class)
public class MaceItemMixin {
    @ModifyReturnValue(
        method = "getAttackDamageBonus(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)F",
        at = @At(
            value = "RETURN",
            ordinal = 2
        )
    )
    private float rebalanceEquipment(float original, Entity target, float baseAttackDamage, DamageSource damageSource, @Local LivingEntity living) {
        return original;
    }

    @ModifyReturnValue(
        method = "getAttackDamageBonus(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)F",
        at = @At(
            value = "RETURN",
            ordinal = 1
        )
    )
    private float rebalanceEquipmentExpected(float original, Entity target, float baseAttackDamage, DamageSource damageSource, @Local LivingEntity living) {
        return original;
    }
}
