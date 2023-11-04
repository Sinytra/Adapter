package dev.su5ed.sinytra.adapter.patch.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.transformer.*;
import dev.su5ed.sinytra.adapter.patch.transformer.filter.InjectionPointTransformerFilter;

import java.util.Objects;

public class MethodTransformSerialization {
    public static final Codec<MethodTransform> METHOD_TRANSFORM_CODEC =
        Codec.STRING.partialDispatch("type", transform -> DataResult.success(getTransformName(transform)), name -> {
            Codec<? extends MethodTransform> entryCodec = MethodTransformSerialization.TRANSFORMER_CODECS.get(name);
            if (entryCodec != null) {
                return DataResult.success(entryCodec);
            }
            return DataResult.error(() -> "Missing codec for transformer " + name);
        });

    private static final BiMap<String, Codec<? extends MethodTransform>> TRANSFORMER_CODECS = ImmutableBiMap.<String, Codec<? extends MethodTransform>>builder()
        .put("disable_mixin", DisableMixin.CODEC)
        .put("change_modified_variable", ChangeModifiedVariableIndex.CODEC)
        .put("modify_injection_point", ModifyInjectionPoint.CODEC)
        .put("modify_injection_target", ModifyInjectionTarget.CODEC)
        .put("modfiy_access", ModifyMethodAccess.CODEC)
        .put("modify_method", ModifyMethodParams.CODEC)
        .put("soft_modify_method", SoftMethodParamsPatch.CODEC)
        .put("injection_point_filter", InjectionPointTransformerFilter.CODEC)
        .build();

    private static String getTransformName(MethodTransform transform) {
        return Objects.requireNonNull(TRANSFORMER_CODECS.inverse().get(transform.codec()), "Missing name for transformer " + transform);
    }
}
