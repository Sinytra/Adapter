package dev.su5ed.sinytra.adapter.patch;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public record LVTOffsets(Map<String, Map<MethodQualifier, List<Offset>>> offsets) {
    public static final Codec<LVTOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(MethodQualifier.CODEC, Offset.CODEC.listOf())).fieldOf("offsets").forGetter(LVTOffsets::offsets)
    ).apply(instance, LVTOffsets::new));

    public record Offset(int index, int amount) {
        public static final Codec<Offset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(Offset::index),
            Codec.INT.fieldOf("amount").forGetter(Offset::amount)
        ).apply(instance, Offset::new));
    }

    public OptionalInt findOffset(String cls, String methodName, String methodDesc, int index) {
        Map<MethodQualifier, List<Offset>> map = this.offsets.get(cls);
        if (map != null) {
            MethodQualifier qualifier = new MethodQualifier(methodName, methodDesc);
            List<Offset> methodOffsets = map.get(qualifier);
            if (methodOffsets != null) {
                int indexOffset = methodOffsets.stream().filter(offset -> offset.index <= index).mapToInt(Offset::amount).sum();
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
