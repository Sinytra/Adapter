package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraMixin {
    @WrapWithCondition(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 2))
    private boolean addRoll2(Camera instance, float yRot, float xRot) {
        return true;
    }

    @WrapWithCondition(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V", ordinal = 1))
    private boolean addRoll2Expected(Camera instance, float yRot, float xRot, float adapter_injected_3) {
        return true;
    }

    @WrapWithCondition(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private boolean addRoll1(Camera instance, float yRot, float xRot) {
        return true;
    }

    @WrapWithCondition(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V", ordinal = 0))
    private boolean addRoll1Expected(Camera instance, float yRot, float xRot, float adapter_injected_3) {
        return true;
    }

    @WrapWithCondition(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 3))
    private boolean addRoll3(Camera instance, float yRot, float xRot) {
        return true;
    }

    @WrapWithCondition(method = "alignWithEntity(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private boolean addRoll3Expected(Camera instance, float yRot, float xRot) {
        return true;
    }
}
