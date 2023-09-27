package dev.su5ed.sinytra.adapter.patch.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.su5ed.sinytra.adapter.patch.ClassPatchInstance;
import dev.su5ed.sinytra.adapter.patch.InterfacePatchInstance;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;

import java.util.List;
import java.util.Objects;

public class PatchSerialization {
    private static final BiMap<String, Codec<? extends PatchInstance>> PATCH_INSTANCE_CODECS = ImmutableBiMap.<String, Codec<? extends PatchInstance>>builder()
        .put("class", ClassPatchInstance.CODEC)
        .put("interface", InterfacePatchInstance.CODEC)
        .build();

    public static final Codec<PatchInstance> PATCH_INSTANCE_CODEC =
        Codec.STRING.partialDispatch("type", transform -> DataResult.success(getPatchInstanceName(transform)), name -> {
            Codec<? extends PatchInstance> entryCodec = PATCH_INSTANCE_CODECS.get(name);
            if (entryCodec != null) {
                return DataResult.success(entryCodec);
            }
            return DataResult.error(() -> "Missing codec for patch instance " + name);
        });

    private static String getPatchInstanceName(PatchInstance instance) {
        return Objects.requireNonNull(PATCH_INSTANCE_CODECS.inverse().get(instance.codec()), "Missing name for patch instance " + instance);
    }

    public static <T> T serialize(List<PatchInstance> patches, DynamicOps<T> dynamicOps) {
        DataResult<T> result = PATCH_INSTANCE_CODEC.listOf().encodeStart(dynamicOps, patches);
        return result.getOrThrow(false, s -> {
            throw new RuntimeException("Error serializing patches: " + s);
        });
    }

    public static <T> List<PatchInstance> deserialize(T patches, DynamicOps<T> dynamicOps) {
        return PATCH_INSTANCE_CODEC.listOf().decode(dynamicOps, patches).getOrThrow(false, s -> {
            throw new RuntimeException("Error deserializing patches: " + s);
        }).getFirst();
    }
}
