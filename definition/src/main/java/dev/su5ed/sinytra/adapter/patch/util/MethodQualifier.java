package dev.su5ed.sinytra.adapter.patch.util;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MethodQualifier(@Nullable String owner, @Nullable String name, @Nullable String desc) {
    private static final Pattern METHOD_QUALIFIER_PATTERN = Pattern.compile("^(?<owner>L.+;)?(?<name>[^(]+)?(?<desc>\\(.*\\).+)?$");

    @Nullable
    public static Optional<MethodQualifier> create(String qualifier) {
        Matcher matcher = METHOD_QUALIFIER_PATTERN.matcher(qualifier);
        if (matcher.matches()) {
            String owner = matcher.group("owner");
            String name = matcher.group("name");
            String desc = matcher.group("desc");
            if (owner != null || name != null || desc != null) {
                return Optional.of(new MethodQualifier(owner, name, desc));
            }
        }
        return Optional.empty();
    }
}
