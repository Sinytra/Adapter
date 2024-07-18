package org.sinytra.adapter.patch.transformer.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.SoftMethodParamsPatch;
import org.sinytra.adapter.patch.transformer.operation.*;
import org.sinytra.adapter.patch.transformer.operation.param.TransformParameters;
import org.sinytra.adapter.patch.transformer.pipeline.MethodTransformationPipeline;

import java.util.Objects;

public class MethodTransformSerialization {
    public static final Codec<MethodTransform> METHOD_TRANSFORM_CODEC =
        Codec.STRING.partialDispatch("type", transform -> DataResult.success(getTransformName(transform)), name -> {
            Codec<? extends MethodTransform> entryCodec = MethodTransformSerialization.TRANSFORMER_CODECS.get(name);
            if (entryCodec != null) {
                return DataResult.success(entryCodec.fieldOf("transform"));
            }
            return DataResult.error(() -> "Missing codec for transformer " + name);
        });

    private static final BiMap<String, Codec<? extends MethodTransform>> TRANSFORMER_CODECS = ImmutableBiMap.<String, Codec<? extends MethodTransform>>builder()
        .put("disable_mixin", DisableMixin.CODEC)
        .put("modify_injection_point", ModifyInjectionPoint.CODEC)
        .put("modify_injection_target", ModifyInjectionTarget.CODEC)
        .put("modfiy_access", ModifyMethodAccess.CODEC)
        .put("modify_method", ModifyMethodParams.CODEC)
        .put("transform_parameters", TransformParameters.CODEC)
        .put("soft_modify_method", SoftMethodParamsPatch.CODEC)
        .put("pipeline_transform", MethodTransformationPipeline.CODEC)
        .build();

    private static String getTransformName(MethodTransform transform) {
        return Objects.requireNonNull(TRANSFORMER_CODECS.inverse().get(transform.codec()), "Missing name for transformer " + transform);
    }
}
