package dev.su5ed.sinytra.adapter.patch;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.su5ed.sinytra.adapter.patch.transformer.*;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Objects;

public class PatchSerialization {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);

    private static final BiMap<String, Codec<? extends MethodTransform>> TRANSFORMER_CODECS = ImmutableBiMap.<String, Codec<? extends MethodTransform>>builder()
        .put("disable_mixin", DisableMixin.CODEC)
        .put("change_modified_variable", ChangeModifiedVariableIndex.CODEC)
        .put("modify_injection_point", ModifyInjectionPoint.CODEC)
        .put("modify_injection_target", ModifyInjectionTarget.CODEC)
        .put("modify_method_params", ModifyMethodParams.CODEC)
        .build();

    public static final Codec<MethodTransform> METHOD_TRANSFORM_CODEC =
        Codec.STRING.partialDispatch("type", transform -> DataResult.success(getTransformName(transform)), name -> {
            Codec<? extends MethodTransform> entryCodec = TRANSFORMER_CODECS.get(name);
            if (entryCodec != null) {
                return DataResult.success(entryCodec);
            }
            return DataResult.error(() -> "Missing codec for transformer " + name);
        });

    private static String getTransformName(MethodTransform transform) {
        return Objects.requireNonNull(TRANSFORMER_CODECS.inverse().get(transform.codec()), "Missing name for transformer " + transform);
    }

    public static <T> T serialize(List<PatchImpl> patches, DynamicOps<T> dynamicOps) {
        DataResult<T> result = PatchImpl.CODEC.listOf().encodeStart(dynamicOps, patches);
        return result.getOrThrow(false, s -> {
            throw new RuntimeException("Error serializing patches: " + s);
        });
    }

    public static <T> List<PatchImpl> deserialize(T patches, DynamicOps<T> dynamicOps) {
        return PatchImpl.CODEC.listOf().decode(dynamicOps, patches).getOrThrow(false, s -> {
            throw new RuntimeException("Error deserializing patches: " + s);
        }).getFirst();
    }
}
