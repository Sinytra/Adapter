package org.sinytra.adapter.test.mixin.pipeline;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// Source: https://github.com/Patbox/polymer/blob/172729bc495a94bf13d7eb6809efddc53f36739e/polymer-core/src/main/java/eu/pb4/polymer/core/mixin/entity/EntityTrackerEntryMixin.java
@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @ModifyVariable(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Consumer<Packet<?>> packetWrap(Consumer<Packet<?>> packetConsumer, @Local(argsOnly = true) ServerPlayer player) {
        return null;
    }

    @ModifyVariable(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private PacketAndPayloadAcceptor<ClientGamePacketListener> packetWrapExpected(PacketAndPayloadAcceptor<ClientGamePacketListener> packetConsumer, @Local(argsOnly = true) ServerPlayer player) {
        return null;
    }

    @Inject(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        at = @At("TAIL")
    )
    private void modifyCreationData(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> sender, CallbackInfo ci) {

    }

    @Inject(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V",
        at = @At("TAIL")
    )
    private void modifyCreationDataExpected(ServerPlayer player, PacketAndPayloadAcceptor<ClientGamePacketListener> sender, CallbackInfo ci) {

    }

    @ModifyArg(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V",
            ordinal = 1
        )
    )
    private Object markAsInitial(Object obj) {
        return obj;
    }

    @ModifyArg(
        method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;accept(Lnet/minecraft/network/protocol/Packet;)Lnet/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor;",
            ordinal = 1
        )
    )
    private Packet<?> markAsInitialExpected(Packet<?> obj) {
        return (Packet<?>) (Object) obj;
    }
}
