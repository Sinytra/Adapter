package org.sinytra.adapter.patch.util;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.gen.AccessorInfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AdapterUtil {
    public static final Codec<Type> TYPE_CODEC = Codec.STRING.xmap(Type::getType, Type::getDescriptor);
    public static final String LAMBDA_PREFIX = "lambda$";
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^:]+)?:(?<desc>.+)?$");
    public static final Type CI_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    public static final Type CIR_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static int getLVTOffsetForType(Type type) {
        return type.equals(Type.DOUBLE_TYPE) || type.equals(Type.LONG_TYPE) ? 2 : 1;
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

    public static void replaceLVT(MethodNode methodNode, Int2IntFunction operator) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
            if (handle == null) continue;

            final int oldValue = handle.get();
            final int newValue = operator.applyAsInt(oldValue);
            if (newValue != oldValue) {
                handle.set(newValue);
            }
        }
    }

    public static boolean canHandleLocalVarInsnValue(AbstractInsnNode insn) {
        return insn instanceof VarInsnNode || insn instanceof IincInsnNode;
    }

    public static int getInsnIntConstValue(AbstractInsnNode insn) {
        return getIntConstValue(insn)
            .orElseThrow(() -> new IllegalArgumentException("Not an int constant opcode: " + insn.getOpcode()));
    }

    public static OptionalInt getIntConstValue(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
            return OptionalInt.of(opcode - Opcodes.ICONST_0);
        }
        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
            return OptionalInt.of(((IntInsnNode) insn).operand);
        }
        return OptionalInt.empty();
    }

    public static AbstractInsnNode getIntConstInsn(int value) {
        if (value >= 1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        } else if (value > 5 && value <= 127) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
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

    public static <T> List<T> getAnnotatedParameters(MethodNode methodNode, Type[] parameters, String annotationDesc, BiFunction<AnnotationNode, Type, T> processor) {
        List<T> list = new ArrayList<>();
        if (methodNode.invisibleParameterAnnotations != null) {
            for (int i = 0; i < methodNode.invisibleParameterAnnotations.length; i++) {
                List<AnnotationNode> parameterAnnotations = methodNode.invisibleParameterAnnotations[i];
                if (parameterAnnotations != null) {
                    for (AnnotationNode paramAnn : parameterAnnotations) {
                        if (annotationDesc.equals(paramAnn.desc)) {
                            Type type = parameters[i];
                            list.add(processor.apply(paramAnn, type));
                        }
                    }
                }
            }
        }
        return list;
    }

    public static Patch.Result applyTransforms(List<MethodTransform> transforms, ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        Patch.Result result = Patch.Result.PASS;
        for (MethodTransform transform : transforms) {
            result = result.or(transform.apply(classNode, methodNode, methodContext, methodContext.patchContext()));
        }
        return result;
    }

    @Nullable
    public static CapturedLocals getCapturedLocals(MethodNode methodNode, MethodContext methodContext) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        OptionalInt paramLocalPos = getCapturedLocalStartingIndex(params);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (paramLocalPos.isEmpty()) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", annotation.getDesc());
            return null;
        }
        MethodContext.TargetPair target = methodContext.findDirtyInjectionTarget();
        if (target == null) {
            return null;
        }
        List<Type> ignored = getAnnotatedParameters(methodNode, params, MixinConstants.SHARE, (node, type) -> type);
        Type[] availableParams = Stream.of(params).filter(t -> !ignored.contains(t)).toArray(Type[]::new);

        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
        int lvtOffset = isStatic ? 0 : 1;
        // The first local var in the method's params comes after the target's params plus the CI/CIR parameter
        int paramLocalPosVal = paramLocalPos.getAsInt();
        // Get expected local variables from method parameters
        List<Type> expected = AdapterUtil.summariseLocals(availableParams, paramLocalPosVal);
        return new CapturedLocals(target, isStatic, paramLocalPosVal, paramLocalPosVal + expected.size(), lvtOffset, expected, new LocalVariableLookup(methodNode));
    }

    private static OptionalInt getCapturedLocalStartingIndex(Type[] params) {
        for (int i = 0; i < params.length; i++) {
            Type param = params[i];
            if ((param.equals(CI_TYPE) || param.equals(CIR_TYPE)) && i + 1 < params.length) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    public record CapturedLocals(MethodContext.TargetPair target, boolean isStatic, int paramLocalStart, int paramLocalEnd, int lvtOffset,
                                 List<Type> expected, LocalVariableLookup lvt) {
    }

    @VisibleForTesting
    public static String methodNodeToString(MethodNode node) {
        Textifier text = new Textifier();
        node.accept(new TraceMethodVisitor(text));
        return toString(text);
    }

    private static String toString(Textifier text) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        text.print(pw);
        pw.flush();
        return sw.toString();
    }

    public static Type getMixinCallableReturnType(MethodNode method) {
        return Type.getReturnType(method.desc) == Type.VOID_TYPE ? CI_TYPE : CIR_TYPE;
    }

    private AdapterUtil() {
    }
}
