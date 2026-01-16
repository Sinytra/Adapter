package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.next.env.ctx.RefMapper;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.*;

public class AtData {
    @NotNull
    private final String value;
    @Nullable
    private final String target;
    @Nullable
    private final Integer ordinal;

    public AtData(@NotNull String value, @Nullable MethodInsnNode target) {
        this(value, target != null ? MethodQualifier.create(target) : null);
    }

    public AtData(@NotNull String value, @Nullable MethodQualifier target) {
        this(value, target != null ? target.asDescriptor() : null, null);
    }

    public AtData(@NotNull String value, @Nullable String target, @Nullable Integer ordinal) {
        this.value = Objects.requireNonNull(value, "Value must not be null");
        this.target = target;
        this.ordinal = ordinal;
    }

    public String getValue() {
        return this.value;
    }

    public Optional<String> getTarget() {
        return Optional.ofNullable(this.target);
    }

    public String getTargetOrThrow() {
        return Objects.requireNonNull(this.target, "No target specified");
    }

    public OptionalInt getOrdinal() {
        return this.ordinal != null ? OptionalInt.of(this.ordinal) : OptionalInt.empty();
    }

    public void apply(AnnotationHandle handle) {
        handle.setOrAppendNonNull("value", this.value);
        handle.setOrAppendNonNull("target", this.target);
        if (this.ordinal != null) {
            handle.setOrAppendNonNull("ordinal", this.ordinal);
        }
    }

    public AnnotationNode toAnnotationNode() {
        AnnotationNode node = new AnnotationNode(MixinConstants.AT);
        node.visit(AT_VALUE, Objects.requireNonNull(this.value));
        node.visit(AT_TARGET, Objects.requireNonNull(this.target));
        if (this.ordinal != null) {
            node.visit(PROPERTY_ORDINAL, this.ordinal);
        }
        return node;
    }

    public AtData withTarget(MethodInsnNode insn) {
        return withTarget(MethodQualifier.create(insn));
    }

    public AtData withTarget(MethodQualifier target) {
        return withTarget(target.asDescriptor());
    }

    public AtData withTarget(String target) {
        return new AtData(this.value, target, this.ordinal);
    }

    public AtData withOrdinal(Integer ordinal) {
        return new AtData(this.value, target, ordinal);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AtData atData = (AtData) o;
        return Objects.equals(value, atData.value) && Objects.equals(target, atData.target) && Objects.equals(ordinal, atData.ordinal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, target, ordinal);
    }

    public static Optional<AtData> parse(AnnotationHandle annotation, RefMapper mapper) {
        String value = annotation.<String>getValue("value").map(AnnotationValueHandle::get).orElse(null);
        if (value == null) {
            return Optional.empty();
        }

        String target = annotation.<String>getValue("target").map(AnnotationValueHandle::get)
            .map(mapper::remap)
            .orElse(null);
        Integer ordinal = annotation.<Integer>getValue("ordinal").map(AnnotationValueHandle::get).orElse(null);

        return Optional.of(new AtData(value, target, ordinal));
    }
}
