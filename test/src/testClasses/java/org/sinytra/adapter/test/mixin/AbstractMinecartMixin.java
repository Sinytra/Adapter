package org.sinytra.adapter.test.mixin;

import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {
    @ModifyArg(method = "moveAlongTrack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private Vec3 modifiedMovement(Vec3 movement) {
        return movement;
    }

    @ModifyArg(method = "moveMinecartOnRail(Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private Vec3 modifiedMovementExpected(Vec3 movement) {
        return movement;
    }
}
