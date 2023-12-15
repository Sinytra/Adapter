package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.gen.AccessorInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdapterUtil {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^:]+)?:(?<desc>.+)?$");
    private static final Logger LOGGER = LogUtils.getLogger();

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
                .map(name -> environment.refmapHolder().remap(owner, name.name)));
    }

    public static String maybeRemapFieldRef(String reference) {
        Matcher matcher = FIELD_REF_PATTERN.matcher(reference);
        if (matcher.matches()) {
            String name = matcher.group("name");
            String desc = matcher.group("desc");
            if (name != null && desc != null) {
                return Objects.requireNonNullElse(matcher.group("owner"), "") + GlobalReferenceMapper.remapReference(name) + ":" + desc;
            }
        }
        return reference;
    }

    @Nullable
    public static SingleValueHandle<Integer> handleLocalVarInsnValue(AbstractInsnNode insn) {
        if (insn instanceof VarInsnNode varInsn) {
            return SingleValueHandle.of(() -> varInsn.var, i -> varInsn.var = i);
        }
        if (insn instanceof IincInsnNode iincInsn) {
            return SingleValueHandle.of(() -> iincInsn.var, i -> iincInsn.var = i);
        }
        return null;
    }

    public static int getInsnIntConstValue(InsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0;
        }
        throw new IllegalArgumentException("Not an int constant opcode: " + opcode);
    }

    public static AbstractInsnNode getIntConstInsn(int value) {
        if (value >= 1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        }
        return new LdcInsnNode(value);
    }

    public static <T extends Interpreter<V>, V extends Value> T analyzeMethod(MethodNode methodNode, T interpreter) {
        Analyzer<V> analyzer = new Analyzer<>(interpreter);
        try {
            analyzer.analyze(methodNode.name, methodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
        return interpreter;
    } 

    private AdapterUtil() {
    }
}
