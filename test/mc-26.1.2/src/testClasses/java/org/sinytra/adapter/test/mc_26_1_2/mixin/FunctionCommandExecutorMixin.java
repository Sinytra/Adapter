package org.sinytra.adapter.test.mc_26_1_2.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.commands.FunctionCommand$1")
public class FunctionCommandExecutorMixin {
    @Inject(method = "arguments", at = @At("HEAD"))
    private void readArguments(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<CompoundTag> cir) {
    }
}
