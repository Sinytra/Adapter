package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.gen.AccessorInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AdapterUtil {
    private static final String MOJMAP_PARAM_NAME_PREFIX = "p_";
    public static final String SHADOW_ANN = "Lorg/spongepowered/asm/mixin/Shadow;";
    // Obfuscated variable names consist of '$$' followed by a number
    private static final String OBF_VAR_PATTERN = "^\\$\\$\\d+$";
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^:]+)?:(?<desc>.+)?$");
    private static final Map<Type, GeneratedVarName> GENERATED_VAR_NAMES = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

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

    public static ClassNode getClassNode(String internalName) {
        return maybeGetClassNode(internalName).orElse(null);
    }

    public static Optional<ClassNode> maybeGetClassNode(String internalName) {
        try {
            return Optional.of(MixinService.getService().getBytecodeProvider().getClassNode(internalName));
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Target class not found: {}", internalName);
            return Optional.empty();
        } catch (Throwable t) {
            LOGGER.debug("Error getting class", t);
            return Optional.empty();
        }
    }

    public static int getLVTIndexForParam(MethodNode method, int paramIndex, Type type) {
        Type[] paramTypes = Type.getArgumentTypes(method.desc);
        int ordinal = 0;
        for (int i = paramIndex - 1; i > 0; i--) {
            if (type.equals(paramTypes[i])) {
                ordinal++;
            }
        }
        List<LocalVariableNode> locals = method.localVariables.stream()
            .sorted(Comparator.comparingInt(lvn -> lvn.index))
            .filter(lvn -> lvn.desc.equals(type.getDescriptor()))
            .toList();
        if (locals.size() > ordinal) {
            return locals.get(ordinal).index;
        }
        return -1;
    }

    public static boolean isAnonymousClass(String name) {
        // Regex: second to last char in class name must be '$', and the class name must end with a number
        return name.matches("^.+\\$\\d+$");
    }

    public static Optional<String> getAccessorTargetFieldName(String owner, MethodNode method, AnnotationHandle annotationHandle, PatchEnvironment environment) {
        return annotationHandle.<String>getValue("value")
            .map(AnnotationValueHandle::get)
            .filter(str -> !str.isEmpty())
            .or(() -> Optional.ofNullable(AccessorInfo.AccessorName.of(method.name))
                .map(name -> environment.remap(owner, name.name)));
    }

    public static String maybeRemapFieldRef(String reference) {
        Matcher matcher = FIELD_REF_PATTERN.matcher(reference);
        if (matcher.matches()) {
            String name = matcher.group("name");
            String desc = matcher.group("desc");
            if (name != null && desc != null) {
                return Objects.requireNonNullElse(matcher.group("owner"), "") + PatchEnvironment.remapReference(name) + ":" + desc;
            }
        }
        return reference;
    }

    private AdapterUtil() {}
}
