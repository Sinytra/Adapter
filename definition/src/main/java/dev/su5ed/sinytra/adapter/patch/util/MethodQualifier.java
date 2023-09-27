package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MethodQualifier(@Nullable String name, @Nullable String desc) {
    private static final Pattern METHOD_QUALIFIER_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^(]+)?(?<desc>\\(.*\\).+)?$");
    public static final Codec<MethodQualifier> CODEC = Codec.STRING.comapFlatMap(
        str -> create(str).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid method qualifier string " + str)),
        qualifier -> Objects.requireNonNullElse(qualifier.name(), "") + Objects.requireNonNullElse(qualifier.desc(), ""));

    @Nullable
    public static Optional<MethodQualifier> create(String qualifier) {
        Matcher matcher = METHOD_QUALIFIER_PATTERN.matcher(qualifier);
        if (matcher.matches()) {
            String name = PatchEnvironment.remapReference(matcher.group("name"));
            String desc = matcher.group("desc");
            if (name != null || desc != null) {
                return Optional.of(new MethodQualifier(name, desc));
            }
        }
        return Optional.empty();
    }
}
