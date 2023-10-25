package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MethodQualifier(@Nullable String owner, @Nullable String name, @Nullable String desc) {
    private static final Pattern METHOD_QUALIFIER_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^(]+)?(?<desc>\\(.*\\).+)?$");
    public static final Codec<MethodQualifier> CODEC = Codec.STRING.comapFlatMap(
        str -> create(str).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid method qualifier string " + str)),
        qualifier -> Objects.requireNonNullElse(qualifier.name(), "") + Objects.requireNonNullElse(qualifier.desc(), ""));

    public MethodQualifier(@Nullable String name, @Nullable String desc) {
        this(null, name, desc);
    }

    @Nullable
    public static Optional<MethodQualifier> create(String qualifier) {
        return create(qualifier, true);
    }

    @Nullable
    public static Optional<MethodQualifier> create(String qualifier, boolean remap) {
        Matcher matcher = METHOD_QUALIFIER_PATTERN.matcher(qualifier);
        if (matcher.matches()) {
            String name = matcher.group("name");
            String desc = matcher.group("desc");
            if (name != null || desc != null) {
                return Optional.of(new MethodQualifier(matcher.group("owner"), remap ? PatchEnvironment.remapReference(name) : name, desc));
            }
        }
        return Optional.empty();
    }

    public boolean matches(MethodQualifier other) {
        return (this.owner == null || other.owner() != null && this.owner.equals(other.owner()))
        && this.name != null && other.name() != null && this.name.equals(other.name())
        && (this.desc == null || other.desc() != null && this.desc.equals(other.desc()));
    }

    public boolean isFull() {
        return this.owner != null && this.name != null && this.desc != null;
    }
}
