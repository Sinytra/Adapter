package org.sinytra.adapter.test.mc_26_1_2.mixin;

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
}
