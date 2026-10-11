package org.sinytra.adapter.model.ann;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.model.constant.MixinAnnotations;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.SLICE_FROM;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.SLICE_ID;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.SLICE_TO;

public class SliceData {
    @Nullable
    private final String id;
    @Nullable
    private final AtData from;
    @Nullable
    private final AtData to;

    public SliceData(@Nullable String id, @Nullable AtData from, @Nullable AtData to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }

    @Nullable
    public String id() {
        return this.id;
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
        String id = handle.<String>getValue(SLICE_ID)
            .map(AnnotationValueHandle::get)
            .orElse(null);
        return new SliceData(id, from, to);
    }

    public AnnotationNode toAnnotationNode() {
        AnnotationNode slice = new AnnotationNode(MixinAnnotations.SLICE);
        if (this.id != null) {
            slice.visit(SLICE_ID, this.id);
        }
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
