package org.sinytra.adapter.util;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.analysis.selector.AnnotationValueHandle;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.spongepowered.asm.mixin.gen.AccessorInfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AdapterUtil {
    public static final String LAMBDA_PREFIX = "lambda$";
    public static final Marker MIXINPATCH = MarkerFactory.getMarker("MIXINPATCH");
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L.+?;)?(?<name>[^:]+)?:(?<desc>.+)?$");
    private static final String DEPRECATED = "Ljava/lang/Deprecated;";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isDeprecated(MethodNode methodNode) {
        return hasAnnotation(methodNode.visibleAnnotations, DEPRECATED);
    }

    public static MethodNode copyMethod(MethodNode original) {
        MethodNode copy = new MethodNode(original.access, original.name, original.desc, original.signature, original.exceptions.toArray(String[]::new));
        original.accept(copy);
        return copy;
    }

    public static int getLVTOffsetForType(Type type) {
        return type.equals(Type.DOUBLE_TYPE) || type.equals(Type.LONG_TYPE) ? 2 : 1;
    }

    public static String randomString(int length) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
            .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
            .limit(length)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
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
                return Objects.requireNonNullElse(matcher.group("owner"), "") + name + ":" + desc;
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
        if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof Integer) {
                return OptionalInt.of((Integer) cst);
            }
        }
        return OptionalInt.empty();
    }

    public static AbstractInsnNode getIntConstInsn(int value) {
        if (value >= 0 && value <= 5) {
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

    public static VarInsnNode loadType(Type type, int index) {
        int opcode = OpcodeUtil.getLoadOpcode(type.getSort());
        return new VarInsnNode(opcode, index);
    }

    public static boolean isShadowField(FieldNode field) {
        return AdapterUtil.hasAnnotation(field.visibleAnnotations, MixinAnnotations.SHADOW);
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

    @Nullable
    public static CapturedLocals getCapturedLocals(MixinContext context, Recipe recipe) {
        TargetPair target = recipe.getDirtyTarget();
        if (target == null) return null;

        return getCapturedLocals(context, target);
    }

    // TODO Better way?
    @Nullable
    public static CapturedLocals getCapturedLocals(MixinContext context, TargetPair dirtyTarget) {
        MethodNode methodNode = context.methodNode();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        OptionalInt paramLocalPos = getCapturedLocalStartingIndex(params);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (paramLocalPos.isEmpty()) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", context.methodAnnotation().getDesc());
            return null;
        }

        List<Type> ignored = getAnnotatedParameters(methodNode, params, MixinAnnotations.SHARE, (node, type) -> type);
        Type[] availableParams = Stream.of(params).filter(t -> !ignored.contains(t)).toArray(Type[]::new);

        boolean isStatic = MethodHelper.isStatic(methodNode);
        int lvtOffset = isStatic ? 0 : 1;
        // The first local var in the method's params comes after the target's params plus the CI/CIR parameter
        int paramLocalPosVal = paramLocalPos.getAsInt();
        // Get expected local variables from method parameters
        List<Type> expected = AdapterUtil.summariseLocals(availableParams, paramLocalPosVal);
        int paramLocalPosEnd = paramLocalPosVal + expected.size() - 1;
        return new CapturedLocals(dirtyTarget, isStatic, paramLocalPosVal, paramLocalPosEnd, lvtOffset, expected, new LocalVariableLookup(methodNode));
    }

    private static OptionalInt getCapturedLocalStartingIndex(Type[] params) {
        for (int i = 0; i < params.length; i++) {
            Type param = params[i];
            if ((param.equals(TypeConstants.CI_TYPE) || param.equals(TypeConstants.CIR_TYPE)) && i + 1 < params.length) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    public static AbstractInsnNode iterateInsns(AbstractInsnNode insn, UnaryOperator<AbstractInsnNode> flow, Predicate<AbstractInsnNode> filter) {
        for (AbstractInsnNode i = flow.apply(insn); i != null; i = flow.apply(i)) {
            if (filter.test(i)) {
                return i;
            }
        }
        return null;
    }

    public static <T> T[] removeArrayElement(T[] arr, int index, IntFunction<T[]> arrayGen) {
        if (arr == null) {
            return null;
        }
        List<T> list = new ArrayList<>(Arrays.asList(arr));
        list.remove(index);
        return list.toArray(arrayGen);
    }

    /**
     * @param paramLocalStart inclusive
     * @param paramLocalEnd inclusive
     */
    public record CapturedLocals(TargetPair target, boolean isStatic, int paramLocalStart, int paramLocalEnd, int lvtOffset,
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

    public static InsnList insnList(AbstractInsnNode... insns) {
        InsnList list = new InsnList();
        for (AbstractInsnNode node : insns) {
            list.add(node);
        }
        return list;
    }

    public static InsnList insnList(List<AbstractInsnNode> insns) {
        InsnList list = new InsnList();
        for (AbstractInsnNode node : insns) {
            list.add(node);
        }
        return list;
    }

    public static List<AbstractInsnNode> subListInsnsExc(AbstractInsnNode from, AbstractInsnNode to) {
        List<AbstractInsnNode> list = new ArrayList<>();
        for (AbstractInsnNode insn = from; insn != null && insn != to; insn = insn.getNext()) {
            list.add(insn);
        }
        return list;
    }

    public static List<AbstractInsnNode> cloneInsns(Collection<AbstractInsnNode> insns) {
        return insns.stream().map(i -> i.clone(Map.of())).toList();
    }

    public static <T> boolean allElementsEqual(Collection<T> list, BiPredicate<T, T> equalityFn) {
        return list.stream()
            .reduce((a, b) -> equalityFn.test(a, b) ? a : null)
            .map(x -> true)
            .orElse(true);
    }

    public static void replaceRangeInclusive(InsnList list, AbstractInsnNode start, AbstractInsnNode end, List<AbstractInsnNode> replacement) {
        InsnList newInsns = AdapterUtil.insnList(replacement);
        list.insertBefore(start, newInsns);

        AbstractInsnNode current = start;
        while (current != null) {
            AbstractInsnNode next = current.getNext();
            list.remove(current);

            if (current == end) {
                break;
            }
            current = next;
        }
    }

    @Nullable
    public static AbstractInsnNode getSingleInsn(SourceValue value) {
        return value.insns.size() == 1 ? value.insns.iterator().next() : null;
    }

    @Nullable
    public static AbstractInsnNode getSingleInsn(List<? extends SourceValue> values, int index) {
        SourceValue value = values.get(index);
        return getSingleInsn(value);
    }

    public static MethodInsnNode box(Type primitive) {
        Type boxed = boxedType(primitive);
        return new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            boxed.getInternalName(),
            "valueOf",
            Type.getMethodDescriptor(boxed, primitive),
            false
        );
    }

    private static Type boxedType(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> Type.getObjectType("java/lang/Boolean");
            case Type.BYTE -> Type.getObjectType("java/lang/Byte");
            case Type.CHAR -> Type.getObjectType("java/lang/Character");
            case Type.SHORT -> Type.getObjectType("java/lang/Short");
            case Type.INT -> Type.getObjectType("java/lang/Integer");
            case Type.FLOAT -> Type.getObjectType("java/lang/Float");
            case Type.LONG -> Type.getObjectType("java/lang/Long");
            case Type.DOUBLE -> Type.getObjectType("java/lang/Double");
            case Type.VOID -> Type.getObjectType("java/lang/Void");
            default -> throw new IllegalStateException("Not a primitive type: " + type);
        };
    }

    private AdapterUtil() {
    }
}
