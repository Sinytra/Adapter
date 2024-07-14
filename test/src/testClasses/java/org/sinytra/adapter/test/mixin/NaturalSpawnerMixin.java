package org.sinytra.adapter.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    // https://github.com/Globox1997/AdventureZ/blob/7f534296038b61cdfb84d6425b5fcc25b75d86e4/src/main/java/net/adventurez/mixin/SpawnHelperMixin.java#L32
    @Inject(method = "mobsAt", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure;FORTRESS_ENEMIES:Lnet/minecraft/util/random/WeightedRandomList;", ordinal = 0), cancellable = true)
    private static void getSpawnEntriesMixin(ServerLevel p_220444_, StructureManager p_220445_, ChunkGenerator p_220446_, MobCategory p_220447_, BlockPos p_220448_, @Nullable Holder<Biome> p_220449_, CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> info) {
        // Noop
    }

    @Inject(method = "mobsAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/StructureManager;registryAccess()Lnet/minecraft/core/RegistryAccess;", ordinal = 0), cancellable = true)
    private static void getSpawnEntriesMixinExpected(ServerLevel p_220444_, StructureManager p_220445_, ChunkGenerator p_220446_, MobCategory p_220447_, BlockPos p_220448_, @Nullable Holder<Biome> p_220449_, CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> info) {
        // Noop
    }
}
