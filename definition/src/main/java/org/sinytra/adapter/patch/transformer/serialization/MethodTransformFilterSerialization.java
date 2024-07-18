package org.sinytra.adapter.patch.transformer.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.sinytra.adapter.patch.api.MethodTransformFilter;
import org.sinytra.adapter.patch.transformer.pipeline.InjectionPointTransformerFilter;

import java.util.Objects;

public class MethodTransformFilterSerialization {
    public static final Codec<MethodTransformFilter> METHOD_TRANSFORM_FILTER_CODEC =
        Codec.STRING.partialDispatch("type", transform -> DataResult.success(getTransformFilterName(transform)), name -> {
            Codec<? extends MethodTransformFilter> entryCodec = MethodTransformFilterSerialization.FILTER_CODECS.get(name);
            if (entryCodec != null) {
                return DataResult.success(entryCodec.fieldOf("transform"));
            }
            return DataResult.error(() -> "Missing codec for transformer filter " + name);
        });

    private static final BiMap<String, Codec<? extends MethodTransformFilter>> FILTER_CODECS = ImmutableBiMap.<String, Codec<? extends MethodTransformFilter>>builder()
        .put("injection_point", InjectionPointTransformerFilter.CODEC)
        .build();

    private static String getTransformFilterName(MethodTransformFilter transform) {
        return Objects.requireNonNull(FILTER_CODECS.inverse().get(transform.codec()), "Missing name for transformer " + transform);
    }
}
