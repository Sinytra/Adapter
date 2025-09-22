package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;

public class SliceData {
    @Nullable
    private final AtData from;
    @Nullable
    private final AtData to;

    public SliceData(@Nullable AtData from, @Nullable AtData to) {
        this.from = from;
        this.to = to;
    }

    public static SliceData parse(AnnotationHandle handle, MixinContext context) {
        AtData from = handle.getNested("from")
            .flatMap(s -> AtData.parse(s, context))
            .orElse(null);
        AtData to = handle.getNested("to")
            .flatMap(s -> AtData.parse(s, context))
            .orElse(null);
        return new SliceData(from, to);
    }
}
