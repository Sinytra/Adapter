package dev.su5ed.sinytra.adapter.patch.util;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.gen.AccessorInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AdapterUtil {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^:]+)?:(?<desc>.+)?$");
    private static final Type CI_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    private static final Type CIR_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
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

    public static boolean canHandleLocalVarInsnValue(AbstractInsnNode insn) {
        return insn instanceof VarInsnNode || insn instanceof IincInsnNode;
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

    public static InsnList insnsWithAdapter(Consumer<InstructionAdapter> consumer) {
        MethodNode dummy = new MethodNode();
        InstructionAdapter adapter = new InstructionAdapter(dummy);
        consumer.accept(adapter);
        return dummy.instructions;
    }

    public static boolean isShadowField(FieldNode field) {
        List<AnnotationNode> annotations = field.visibleAnnotations != null ? field.visibleAnnotations : List.of();
        return AdapterUtil.hasAnnotation(annotations, MixinConstants.SHADOW);
    }

    public static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
        return annotations != null && annotations.stream().anyMatch(an -> desc.equals(an.desc));
    }

    // Adapted from org.spongepowered.asm.mixin.injection.callback.CallbackInjector summariseLocals
    public static <T> List<T> summariseLocals(T[] locals, int pos) {
        List<T> list = new ArrayList<>();
        if (locals != null) {
            for (int i = pos; i < locals.length; i++) {
                if (locals[i] != null) {
                    list.add(locals[i]);
                }
            }
        }
        return list;
    }

    @Nullable
    public static CapturedLocals getCapturedLocals(MethodNode methodNode, MethodContext methodContext) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (Stream.of(params).noneMatch(p -> p.equals(CI_TYPE) || p.equals(CIR_TYPE))) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", annotation.getDesc());
            return null;
        }
        MethodContext.TargetPair target = methodContext.findDirtyInjectionTarget();
        if (target == null) {
            return null;
        }
        Type[] targetParams = Type.getArgumentTypes(target.methodNode().desc);
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
        int lvtOffset = isStatic ? 0 : 1;
        // The first local var in the method's params comes after the target's params plus the CI/CIR parameter
        int paramLocalPos = targetParams.length + 1;
        // Get expected local variables from method parameters
        List<Type> expected = AdapterUtil.summariseLocals(params, paramLocalPos);
        return new CapturedLocals(target, isStatic, paramLocalPos, paramLocalPos + expected.size(), lvtOffset, expected, new LocalVariableLookup(methodNode.localVariables));
    }

    public record CapturedLocals(MethodContext.TargetPair target, boolean isStatic, int paramLocalStart, int paramLocalEnd, int lvtOffset,
                                 List<Type> expected, LocalVariableLookup lvt) {
    }

    private AdapterUtil() {
    }
}
