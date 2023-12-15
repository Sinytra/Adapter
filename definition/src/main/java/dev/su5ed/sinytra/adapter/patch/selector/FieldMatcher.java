package dev.su5ed.sinytra.adapter.patch.selector;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FieldMatcher {
    public static final Codec<FieldMatcher> CODEC = Codec.STRING.xmap(FieldMatcher::new, field -> field.name + Objects.requireNonNullElse(field.desc, ""));

    private final String name;
    @Nullable
    private final String desc;

    public FieldMatcher(String field) {
        int descIndex = field.indexOf(':');
        String name = descIndex == -1 ? field : field.substring(0, descIndex);
        this.name = GlobalReferenceMapper.remapReference(name);
        this.desc = descIndex == -1 ? null : field.substring(descIndex + 1);
    }

    public String getName() {
        return this.name;
    }

    public boolean matches(FieldMatcher other) {
        return matches(other.name, other.desc);
    }

    public boolean matches(String name, String desc) {
        return this.name.equals(name) && (this.desc == null || desc == null || this.desc.equals(desc));
    }
}
