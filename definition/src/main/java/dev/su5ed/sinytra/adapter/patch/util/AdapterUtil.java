package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AdapterUtil {
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
                new GeneratedVarName(Type.BOOLEAN_TYPE, "flag"),
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

    public static class GeneratedVarName {
        private final Set<Type> types;
        private final Pattern pattern;

        public GeneratedVarName(Type type, String prefix) {
            this(Set.of(type), Set.of(prefix));
        }

        public GeneratedVarName(Set<Type> types, Set<String> prefixes) {
            this.types = types;
            String matchingPrefixes = String.join("|", prefixes);
            String patternString = "^(%s)\\d*$".formatted(matchingPrefixes);
            this.pattern = Pattern.compile(patternString);
        }

        public Set<Type> getTypes() {
            return this.types;
        }

        public boolean test(String str) {
            return this.pattern.matcher(str).matches();
        }
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

    public static int getLVTOffsetForType(Type type) {
        return type.equals(Type.DOUBLE_TYPE) || type.equals(Type.LONG_TYPE) ? 2 : 1;
    }

    private AdapterUtil() {}
}
