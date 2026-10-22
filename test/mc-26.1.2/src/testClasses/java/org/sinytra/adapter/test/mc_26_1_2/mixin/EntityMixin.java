package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.sounds.SoundEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public class EntityMixin {
    @Unique
    private final Entity self = (Entity) (Object) this;

    @ModifyArg(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"), index = 1)
    private float adjustStepSoundVolume(float original) {
        return original * self.getBbWidth();
    }

    @Unique
    public float adjustStepSoundVolumeExpected(float original) {
        return original * self.getBbWidth();
    }

    @ModifyArg(method = "playMuffledStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"), index = 1)
    private float adjustMuffledStepSoundVolume(float original) {
        return original * self.getBbWidth();
    }

    @Unique
    public float adjustMuffledStepSoundVolumeExpected(float original) {
        return original * self.getBbWidth();
    }

    @ModifyArg(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"), index = 1)
    private float adjustStepSoundVolumeOfThis(float original) {
        return original * ((Entity) (Object) this).getBbWidth();
    }

    @Unique
    public float adjustStepSoundVolumeOfThisExpected(float original) {
        return original * ((Entity) (Object) this).getBbWidth();
    }

    @ModifyArg(method = "playCombinationStepSounds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"), index = 1)
    private float adjustCombinationStepSoundVolume(float original) {
        return original * self.getBbWidth();
    }

    @Unique
    public float adjustCombinationStepSoundVolumeExpected(float original) {
        return original * self.getBbWidth();
    }

    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"))
    private void wrapStepSound(Entity instance, SoundEvent sound, float volume, float pitch, Operation<Void> original) {
        if (!self.isSilent()) {
            original.call(instance, sound, volume, pitch);
        }
    }

    @Unique
    public void wrapStepSoundExpected(Entity instance, SoundEvent sound, float volume, float pitch, Operation<Void> original) {
        if (!self.isSilent()) {
            original.call(instance, sound, volume, pitch);
        }
    }
}
