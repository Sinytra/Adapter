package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TripWireBlock;
import net.neoforged.neoforge.common.ItemAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TripWireBlock.class)
public class TripWireBlockMixin {
    // https://github.com/quiqueck/BCLib/blob/53349085e5d3adb20a023f29d9aab85acce58332/src/main/java/org/betterx/bclib/mixin/common/shears/TripWireBlockMixin.java#L17
    @WrapOperation(
        method = "playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"
        )
    )
    private boolean isShears(ItemStack instance, Item item, Operation<Boolean> original) {
        return original.call(instance, item) || item == Items.SHEARS && instance.isEmpty();
    }

    @WrapOperation(
        method = "playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/neoforged/neoforge/common/ItemAbility;)Z"
        )
    )
    private boolean isShearsExpected(ItemStack instance, ItemAbility item, Operation<Boolean> original) {
        return original.call(instance, item) || instance.getItem() == Items.SHEARS && instance.isEmpty();
    }
}
