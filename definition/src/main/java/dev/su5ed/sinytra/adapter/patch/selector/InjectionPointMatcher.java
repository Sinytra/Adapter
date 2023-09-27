package dev.su5ed.sinytra.adapter.patch.selector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;

public record InjectionPointMatcher(@Nullable String value, String target) {
    public static final Codec<InjectionPointMatcher> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("value").forGetter(i -> Optional.ofNullable(i.value())),
        Codec.STRING.fieldOf("target").forGetter(InjectionPointMatcher::target)
    ).apply(instance, InjectionPointMatcher::new));

    public InjectionPointMatcher(Optional<String> value, String target) {
        this(value.orElse(null), target);
    }

    public InjectionPointMatcher(@Nullable String value, String target) {
        this.value = value;

        Matcher matcher = Patch.METHOD_REF_PATTERN.matcher(target);
        if (matcher.matches()) {
            String owner = matcher.group("owner");
            String name = matcher.group("name");
            String desc = matcher.group("desc");

            String mappedName = PatchEnvironment.remapReference(name);
            this.target = Objects.requireNonNullElse(owner, "") + mappedName + Objects.requireNonNullElse(desc, "");
        } else {
            this.target = target;
        }
    }

    public boolean test(String value, String target) {
        return this.target.equals(target) && (this.value == null || this.value.equals(value));
    }
}
