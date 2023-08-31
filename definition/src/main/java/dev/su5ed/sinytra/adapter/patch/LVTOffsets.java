package dev.su5ed.sinytra.adapter.patch;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public record LVTOffsets(Map<String, Map<MethodQualifier, List<Integer>>> offsets) {
    public static final Codec<LVTOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(MethodQualifier.CODEC, Codec.INT.listOf())).fieldOf("offsets").forGetter(LVTOffsets::offsets)
    ).apply(instance, LVTOffsets::new));

    public OptionalInt findOffset(String cls, String methodName, String methodDesc, int index) {
        Map<MethodQualifier, List<Integer>> map = this.offsets.get(cls);
        if (map != null) {
            MethodQualifier qualifier = new MethodQualifier(methodName, methodDesc);
            List<Integer> methodOffsets = map.get(qualifier);
            if (methodOffsets != null) {
                int indexOffset = (int) methodOffsets.stream().filter(idx -> idx <= index).count();
                return OptionalInt.of(indexOffset);
            }
        }
        return OptionalInt.empty();
    }

    public static LVTOffsets fromJson(JsonElement json) {
        return CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, s -> {
            throw new RuntimeException("Error deserializing lvt offsets: " + s);
        }).getFirst();
    }

    public JsonElement toJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow(false, s -> {
            throw new RuntimeException("Error serializing lvt offsets: " + s);
        });
    }
}
