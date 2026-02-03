package org.sinytra.adapter.env.ann;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.env.ctx.RefMapper;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.SLICE_FROM;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.SLICE_TO;

public class SliceData {
    @Nullable
    private final AtData from;
    @Nullable
    private final AtData to;

    public SliceData(@Nullable AtData from, @Nullable AtData to) {
        this.from = from;
        this.to = to;
    }

    @Nullable
    public AtData from() {
        return this.from;
    }

    @Nullable
    public AtData to() {
        return this.to;
    }

    public static SliceData parse(AnnotationHandle handle, RefMapper context) {
        AtData from = handle.getNested(SLICE_FROM)
            .flatMap(s -> AtData.parse(s, context))
            .orElse(null);
        AtData to = handle.getNested(SLICE_TO)
            .flatMap(s -> AtData.parse(s, context))
            .orElse(null);
        return new SliceData(from, to);
    }

    public AnnotationNode toAnnotationNode() {
        AnnotationNode slice = new AnnotationNode(MixinAnnotations.SLICE);
        if (this.from != null) {
            AnnotationVisitor fromNode = slice.visitAnnotation(SLICE_FROM, MixinAnnotations.AT);
            this.from.toAnnotationNode().accept(fromNode);
        }
        if (this.to != null) {
            AnnotationVisitor fromNode = slice.visitAnnotation(SLICE_TO, MixinAnnotations.AT);
            this.to.toAnnotationNode().accept(fromNode);
        }
        return slice;
    }
}
