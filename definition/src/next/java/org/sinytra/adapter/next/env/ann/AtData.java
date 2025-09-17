package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public class AtData {
    @NotNull
    private final String value;
    @Nullable
    private final String target;
    @Nullable
    private final Integer ordinal;

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

    public static Optional<AtData> parse(AnnotationHandle annotation) {
        String value = annotation.<String>getValue("value").map(AnnotationValueHandle::get).orElse(null);
        if (value == null) {
            return Optional.empty();
        }

        String target = annotation.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
        Integer ordinal = annotation.<Integer>getValue("ordinal").map(AnnotationValueHandle::get).orElse(null);

        return Optional.of(new AtData(value, target, ordinal));
    }
}
