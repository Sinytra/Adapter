package org.sinytra.adapter.patch;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public record LVTOffsets(Map<String, Map<MethodQualifier, List<Swap>>> reorders) {
    public static final Codec<LVTOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(MethodQualifier.CODEC, Swap.CODEC.listOf())).fieldOf("reorders").forGetter(LVTOffsets::reorders)
    ).apply(instance, LVTOffsets::new));

    public record Swap(int original, int modified) {
        public static final Codec<Swap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("original").forGetter(Swap::original),
            Codec.INT.fieldOf("modified").forGetter(Swap::modified)
        ).apply(instance, Swap::new));
    }

    public OptionalInt findReorder(String cls, String methodName, String methodDesc, int index) {
        Map<MethodQualifier, List<Swap>> map = this.reorders.get(cls);
        if (map != null) {
            MethodQualifier qualifier = new MethodQualifier(methodName, methodDesc);
            List<Swap> methodReorders = map.get(qualifier);
            if (methodReorders != null) {
                for (Swap swap : methodReorders) {
                    if (swap.original() == index) {
                        return OptionalInt.of(swap.modified());
                    }
                }
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
