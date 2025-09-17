package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.Nullable;
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

    public static SliceData parse(AnnotationHandle handle) {
        AtData from = handle.getNested("from")
            .flatMap(AtData::parse)
            .orElse(null);
        AtData to = handle.getNested("to")
            .flatMap(AtData::parse)
            .orElse(null);
        return new SliceData(from, to);
    }
}
