package org.sinytra.adapter.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(BoatRenderer.class)
public class BoatRendererMixin {
    // https://github.com/TerraformersMC/Terraform/blob/0d569ebf8e6c78c79ccdb8b8f28c081089764aef/terraform-wood-api-v1/src/main/java/com/terraformersmc/terraform/boat/impl/mixin/MixinBoatEntityRenderer.java#L24
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getBoatTextureAndModel(Map<Boat.Type, Pair<ResourceLocation, ListModel<Boat>>> instance, Object type, Operation<Object> original, Boat entity) {
        return original.call(instance, type);
    }

    @WrapOperation(method = "getModelWithLocation(Lnet/minecraft/world/entity/vehicle/Boat;)Lcom/mojang/datafixers/util/Pair;", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getBoatTextureAndModelExpected(Map<Boat.Type, Pair<ResourceLocation, ListModel<Boat>>> instance, Object type, Operation<Object> original, Boat entity) {
        return original.call(instance, type);
    }
}
