package org.sinytra.adapter.test.mixin.pipeline;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
    // https://github.com/DiemondPlayer/Unidye/blob/1.21/src/main/java/net/diemond_player/unidye/mixin/BeaconBlockEntityMixin.java
    @Redirect(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I")
    )
    private static int replaceDyeColor(DyeColor instance, @Local(ordinal = 1) BlockPos pos, @Local(argsOnly = true) Level level) {
        return instance.getTextureDiffuseColor();
    }

    @Redirect(
        method = "getBeaconColorMultiplier(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Integer;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I")
    )
    private static int replaceDyeColorExpected(DyeColor instance, @Local(ordinal = 1) BlockPos pos, @Local(argsOnly = true) Level level) {
        return instance.getTextureDiffuseColor();
    }

    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void captureBeamBlock(Level level, BlockPos pos, BlockState state, BeaconBlockEntity blockEntity, CallbackInfo ci, int i, int j, int k, BlockPos blockPos, @Coerce Object section, int l, int m, BlockState blockState, Block block, BeaconBeamBlock beamBlock) {
        System.out.println(beamBlock);
    }

    @Inject(
        method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void captureBeamBlockExpected(Level level, BlockPos pos, BlockState state, BeaconBlockEntity blockEntity, CallbackInfo ci, int i, int j, int k, BlockPos blockPos, @Coerce Object section, int l, int m, BlockState blockState, Block block, BeaconBeamBlock beamBlock) {
        System.out.println(beamBlock);
    }
}
