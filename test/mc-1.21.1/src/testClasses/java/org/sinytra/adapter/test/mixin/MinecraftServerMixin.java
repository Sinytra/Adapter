package org.sinytra.adapter.test.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    // https://github.com/jacobsjo/worldgen-devtools/blob/124d800695aa2969bf25a4efc18c485bc8fa1564/src/main/java/eu/jacobsjo/worldgendevtools/reloadregistries/mixin/MinecraftServerMixin.java#L40
    @Inject(
        method = "lambda$reloadResources$28(Lcom/google/common/collect/ImmutableList;)Ljava/util/concurrent/CompletionStage;",
        at = @At("HEAD")
    )
    private void thenCompose(ImmutableList<?> immutableList, CallbackInfoReturnable<?> cir) {
        // Noop
    }

    @Inject(
        method = "lambda$reloadResources$29(Lcom/google/common/collect/ImmutableList;)Ljava/util/concurrent/CompletionStage;",
        at = @At("HEAD")
    )
    private void thenComposeExpected(ImmutableList<?> immutableList, CallbackInfoReturnable<?> cir) {
        // Noop
    }
}
