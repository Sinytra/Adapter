package org.sinytra.adapter.patch.api;

import com.mojang.serialization.Codec;

public interface MethodTransformFilter {
    Codec<? extends MethodTransformFilter> codec();

    boolean test(MethodContext methodContext);
}
