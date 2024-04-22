package org.sinytra.adapter.patch.transformer.param;

import com.mojang.serialization.Codec;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.selector.AnnotationHandle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public enum ParamTransformTarget {
    ALL,
    METHOD(MixinConstants.INJECT, MixinConstants.OVERWRITE, MixinConstants.MODIFY_VAR),
    INJECTION_POINT(MixinConstants.REDIRECT, MixinConstants.MODIFY_ARG, MixinConstants.MODIFY_ARGS, MixinConstants.WRAP_OPERATION),
    METHOD_EXT(MixinConstants.INJECT, MixinConstants.REDIRECT, MixinConstants.OVERWRITE, MixinConstants.MODIFY_VAR);

    public static final Codec<ParamTransformTarget> CODEC = Codec.STRING.xmap(ParamTransformTarget::from, ParamTransformTarget::name);

    private final Set<String> targetMixinTypes;

    ParamTransformTarget(String... targetMixinTypes) {
        this.targetMixinTypes = new HashSet<>(Arrays.asList(targetMixinTypes));
    }

    public static ParamTransformTarget from(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    public Set<String> getTargetMixinTypes() {
        return this.targetMixinTypes;
    }

    public boolean test(AnnotationHandle methodAnnotation) {
        return this.targetMixinTypes.contains(methodAnnotation.getDesc());
    }
}
