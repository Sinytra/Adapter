package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MethodQualifier(@Nullable String owner, @Nullable String name, @Nullable String desc) {
    private static final Pattern METHOD_QUALIFIER_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^(:]+)?(?<desc>\\((?:\\[*[ZCBSIFJD]|\\[*L[a-zA-Z0-9/_$]+;)*\\)(?:\\[*[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_;$]+))?$");
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

    public String internalOwnerName() {
        return Type.getType(this.owner).getInternalName();
    }

    public boolean matches(MethodQualifier other) {
        return matches(other.owner(), other.name(), other.desc());
    }

    public boolean matches(MethodInsnNode insn) {
        return matches(Type.getObjectType(insn.owner).getDescriptor(), insn.name, insn.desc);
    }

    public boolean matches(@Nullable String owner, @Nullable String name, @Nullable String desc) {
        return (this.owner == null || this.owner.equals(owner))
            && this.name != null && this.name.equals(name)
            && (this.desc == null || this.desc.equals(desc));
    }

    public boolean isFull() {
        return this.owner != null && this.name != null && this.desc != null;
    }
}
