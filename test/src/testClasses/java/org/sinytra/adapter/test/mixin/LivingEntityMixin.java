package org.sinytra.adapter.test.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // https://github.com/TheDeathlyCow/frostiful/blob/5f0a696400de2ae49b21ff42af5116f058cf13ae/src/main/java/com/github/thedeathlycow/frostiful/mixins/entity/ice_skating/LivingEntityMovementMixin.java#L141
    @ModifyVariable(
        method = "travel",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/LivingEntity;onGround()Z"
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
            target = "Lnet/minecraft/world/entity/LivingEntity;onGround()Z"
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

    // https://github.com/Earthcomputer/clientcommands/blob/b5ed9155bdab5606498f6dc6538e5a6bdfad3b70/src/main/java/net/earthcomputer/clientcommands/mixin/rngevents/LivingEntityMixin.java#L60
    @Inject(method = "baseTick",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/tags/FluidTags;WATER:Lnet/minecraft/tags/TagKey;", ordinal = 0)),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0))
    public void onUnderwater(CallbackInfo ci) {
        ourUniqueMethod(); // Prevent extraction
    }

    @Unique
    public void onUnderwaterExpected(CallbackInfo ci) {
        ourUniqueMethod();
    }

    // https://github.com/Earthcomputer/clientcommands/blob/b5ed9155bdab5606498f6dc6538e5a6bdfad3b70/src/main/java/net/earthcomputer/clientcommands/mixin/rngevents/LivingEntityMixin.java#L83
    @Inject(method = "baseTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;isClientSide:Z", ordinal = 2))
    public void testFrostWalker(CallbackInfo ci) {
        ourUniqueMethod();
    }

    @Unique
    public void testFrostWalkerExpected(CallbackInfo ci) {
        ourUniqueMethod();
    }

    @Unique
    private void ourUniqueMethod() {
        // Noop
    }
}
