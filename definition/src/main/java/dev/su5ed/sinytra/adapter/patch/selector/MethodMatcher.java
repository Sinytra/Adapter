package dev.su5ed.sinytra.adapter.patch.selector;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class MethodMatcher {
    public static final Codec<MethodMatcher> CODEC = Codec.STRING.xmap(MethodMatcher::new, matcher -> matcher.name + Objects.requireNonNullElse(matcher.desc, ""));

    private final String name;
    @Nullable
    private final String desc;

    public MethodMatcher(String method) {
        int descIndex = method.indexOf('(');
        String name = descIndex == -1 ? method : method.substring(0, descIndex);
        this.name = PatchEnvironment.remapMethodName(name);
        this.desc = descIndex == -1 ? null : method.substring(descIndex);
    }

    public boolean matches(String name, String desc) {
        return this.name.equals(name) && (this.desc == null || desc == null || this.desc.equals(desc));
    }
}
