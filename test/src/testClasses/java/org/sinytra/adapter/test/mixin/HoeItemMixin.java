package org.sinytra.adapter.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(HoeItem.class)
public class HoeItemMixin {
    // https://github.com/AViewFromTheTop/SimpleCopperPipesMC/blob/8173eebc90d4acfffce7e1cf150c970f4e755ed4/src/main/java/net/lunade/copper/mixin/HoeItemMixin.java#L28
    @Inject(
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/HoeItem;TILLABLES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC, ordinal = 0),
        method = "useOn",
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    public void injectUseOn(UseOnContext itemUsageContext, CallbackInfoReturnable<InteractionResult> info, Level level, BlockPos blockPos) {
        // Noop
    }

    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "useOn",
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    public void injectUseOnExpected(UseOnContext itemUsageContext, CallbackInfoReturnable<InteractionResult> info, Level level, BlockPos blockPos) {
        // Noop
    }
}
