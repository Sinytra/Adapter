package dev.su5ed.sinytra.adapter.patch.util;

import com.google.common.base.Strings;
import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GeneratedVariables {
    private static final String MOJMAP_PARAM_NAME_PREFIX = "p_";
    // Obfuscated variable names consist of '$$' followed by a number
    private static final String OBF_VAR_PATTERN = "^\\$\\$\\d+$";
    private static final Map<Type, GeneratedVarName> GENERATED_VAR_NAMES = new HashMap<>();

    static {
        // Source: https://github.com/MinecraftForge/ForgeFlower/blob/7534b3cac6bb8b93a03e3b39533e0bab38de9a61/FernFlower-Patches/0011-JAD-Style-variable-naming.patch#L574-L589
        Stream.of(
                new GeneratedVarName(Set.of(Type.INT_TYPE, Type.LONG_TYPE), Set.of("i", "j", "k", "l")),
                new GeneratedVarName(Type.BYTE_TYPE, "b"),
                new GeneratedVarName(Type.CHAR_TYPE, "c"),
                new GeneratedVarName(Type.SHORT_TYPE, "short"),
                new GeneratedVarName(Set.of(Type.BOOLEAN_TYPE), Set.of("flag", "bl")),
                new GeneratedVarName(Type.DOUBLE_TYPE, "d"),
                new GeneratedVarName(Type.FLOAT_TYPE, "f"),
                new GeneratedVarName(Type.getObjectType("java/io/File"), "file"),
                new GeneratedVarName(Type.getObjectType("java/lang/String"), "s"),
                new GeneratedVarName(Type.getObjectType("java/lang/Class"), "oclass"),
                new GeneratedVarName(Type.getObjectType("java/lang/Long"), "olong"),
                new GeneratedVarName(Type.getObjectType("java/lang/Byte"), "obyte"),
                new GeneratedVarName(Type.getObjectType("java/lang/Short"), "oshort"),
                new GeneratedVarName(Type.getObjectType("java/lang/Boolean"), "obool"),
                new GeneratedVarName(Type.getObjectType("java/lang/Package"), "opackage"),
                new GeneratedVarName(Type.getObjectType("java/lang/Enum"), "oenum")
            )
            .flatMap(generatedVarName -> generatedVarName.getTypes().stream()
                .map(type -> Pair.of(type, generatedVarName)))
            .forEach(pair -> {
                if (GENERATED_VAR_NAMES.put(pair.getFirst(), pair.getSecond()) != null) {
                    throw new IllegalArgumentException("Duplicate generator for type " + pair.getFirst().getDescriptor());
                }
            });
    }

    public static boolean isGeneratedVariableName(String name, Type type) {
        if (name.startsWith(MOJMAP_PARAM_NAME_PREFIX) || name.matches(OBF_VAR_PATTERN)) {
            return true;
        }
        GeneratedVarName generator = GENERATED_VAR_NAMES.get(type);
        boolean knownGenerated = generator != null && generator.test(name);
        if (!knownGenerated && type.getSort() == Type.OBJECT && !name.equals("this")) {
            String internalName = type.getInternalName();
            int index = internalName.lastIndexOf('/');
            String shortName = internalName.substring(index + 1).toLowerCase(Locale.ROOT);
            String pattern = "^(\\Q%s\\E)\\d*$".formatted(shortName);
            return name.matches(pattern);
        }
        return knownGenerated;
    }

    public static OptionalInt getGeneratedVariableOrdinal(String name, Type type) {
        GeneratedVarName generator = GENERATED_VAR_NAMES.get(type);
        return generator != null ? generator.getOrdinal(name) : OptionalInt.empty();
    }

    public static class GeneratedVarName {
        private final Set<Type> types;
        private final Pattern pattern;

        public GeneratedVarName(Type type, String prefix) {
            this(Set.of(type), Set.of(prefix));
        }

        public GeneratedVarName(Set<Type> types, Set<String> prefixes) {
            this.types = types;
            String matchingPrefixes = String.join("|", prefixes);
            String patternString = "^(%s)(?<index>\\d*)$".formatted(matchingPrefixes);
            this.pattern = Pattern.compile(patternString);
        }

        public Set<Type> getTypes() {
            return this.types;
        }

        public boolean test(String str) {
            return this.pattern.matcher(str).matches();
        }

        public OptionalInt getOrdinal(String str) {
            Matcher matcher = this.pattern.matcher(str);
            if (matcher.matches()) {
                String ordinal = matcher.group("index");
                if (Strings.isNullOrEmpty(ordinal)) {
                    return OptionalInt.of(0);
                }
                int intOrdinal = Integer.parseInt(ordinal);
                return OptionalInt.of(intOrdinal);
            }
            return OptionalInt.empty();
        }
    }
}
