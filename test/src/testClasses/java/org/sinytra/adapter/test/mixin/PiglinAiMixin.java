package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PiglinAi.class)
public class PiglinAiMixin {
    // https://github.com/quiqueck/BetterNether/blob/e1c5bea37001728844d16feec3ef3b3f14ae5139/src/main/java/org/betterx/betternether/mixin/common/piglin/PiglinAiMixin.java
    @WrapOperation(
        method = "isWearingGold(Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private static boolean isWearingGold(Holder<ArmorMaterial> instance, Holder<ArmorMaterial> tHolder, Operation<Boolean> original) {
        return original.call(instance, tHolder) || instance.is(ArmorMaterials.DIAMOND);
    }

    @WrapOperation(
        method = "isWearingGold(Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;makesPiglinsNeutral(Lnet/minecraft/world/entity/LivingEntity;)Z"
        )
    )
    private static boolean isWearingGoldExpected(ItemStack instance, LivingEntity tHolder, Operation<Boolean> original) {
        if (!(instance.getItem() instanceof ArmorItem)) {
            return original.call(instance, tHolder);
        }
        return original.call(instance, tHolder) || ((ArmorItem) instance.getItem()).getMaterial().is(ArmorMaterials.DIAMOND);
    }
}
