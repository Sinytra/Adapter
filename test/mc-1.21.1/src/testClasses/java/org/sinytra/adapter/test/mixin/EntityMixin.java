package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
    // https://github.com/Soulphur0/ElytraAeronautics/blob/7952ea7f7f4706d8fe6f569fb4dfbfee3dc921bb/src/main/java/com/github/Soulphur0/mixin/EntityMixin.java#L35
    @ModifyExpressionValue(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;touchingUnloadedChunk()Z"))
    private boolean bypassMovementInFluidCalls(boolean original) {
        return original;
    }

    @ModifyExpressionValue(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;touchingUnloadedChunk()Z"))
    private boolean bypassMovementInFluidCallsExpected(boolean original) {
        return original;
    }

    // https://github.com/Maxmani/arcadian-dream/blob/46aef7b6c35790ccc4b7a9236aa25907cc743d94/src/main/java/net/reimaden/arcadiandream/mixin/EntityMixin.java#L28
    @ModifyExpressionValue(
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;length()D",
            ordinal = 0
        )
    )
    private double preventPushFromFluids(double original) {
        return original;
    }

    @ModifyExpressionValue(
        method = "lambda$updateFluidHeightAndDoFluidPushing$22(Lnet/neoforged/neoforge/fluids/FluidType;Lnet/minecraft/world/entity/Entity$1InterimCalculation;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;length()D",
            ordinal = 0
        )
    )
    private double preventPushFromFluidsExpected(double original) {
        return original;
    }
}
