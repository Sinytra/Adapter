package org.sinytra.adapter.test.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // https://github.com/TheDeathlyCow/frostiful/blob/5f0a696400de2ae49b21ff42af5116f058cf13ae/src/main/java/com/github/thedeathlycow/frostiful/mixins/entity/ice_skating/LivingEntityMovementMixin.java#L141
    @ModifyVariable(
        method = "travel",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/Entity;onGround()Z"
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/Block;getFriction()F"
            )
        )
    )
    private float getSlipperinessForIceSkates(float slipperiness) {
        return slipperiness;
    }

    @ModifyVariable(
        method = "travel",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/Entity;onGround()Z"
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"
            )
        )
    )
    private float getSlipperinessForIceSkatesExpected(float slipperiness) {
        return slipperiness;
    }
}
