package org.sinytra.adapter.util;

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
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AdapterUtil {
    public static final Marker MIXINPATCH = MarkerFactory.getMarker("MIXINPATCH");

    @Nullable
    public static MethodNode findMethod(ClassNode cls, String name, String desc) {
        for (MethodNode method : cls.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    public static MethodNode copyMethod(MethodNode original) {
        MethodNode copy = new MethodNode(original.access, original.name, original.desc, original.signature, original.exceptions.toArray(String[]::new));
        original.accept(copy);
        return copy;
    }

    public static boolean isAnonymousClass(String name) {
        // Regex: second to last char in class name must be '$', and the class name must end with a number
        return name.matches("^.+\\$\\d+$");
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
        return new VarInsnNode(OpcodeUtil.getLoadOpcode(type), index);
    }

    public static boolean isShadowField(FieldNode field) {
        return AdapterUtil.hasAnnotation(field.visibleAnnotations, MixinAnnotations.SHADOW);
    }

    public static boolean hasAnnotation(@Nullable List<AnnotationNode> annotations, String desc) {
        return getAnnotation(annotations, desc) != null;
    }

    @Nullable
    public static AnnotationNode getAnnotation(@Nullable List<AnnotationNode> annotations, String desc) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (desc.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getAnnotationValue(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) {
                    return (T) annotation.values.get(i + 1);
                }
            }
        }
        return null;
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

    public static <T> Optional<T> findOne(Collection<T> candidates, Predicate<? super T> predicate) {
        T match = null;
        for (T candidate : candidates) {
            if (predicate.test(candidate)) {
                if (match != null) {
                    return Optional.empty();
                }
                match = candidate;
            }
        }
        return Optional.ofNullable(match);
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
    public static <T> T getSingleOrNull(Collection<T> collection) {
        return collection.size() == 1 ? collection.iterator().next() : null;
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

    public static String shortenPackage(String pkg) {
        int first = pkg.indexOf('.');
        if (first < 0) return pkg;
        int second = pkg.indexOf('.', first + 1);
        return second < 0 ? pkg : pkg.substring(0, second);
    }

    public static String internalNameToPkg(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash < 0) return "";
        return internalName.substring(0, lastSlash).replace('/', '.');
    }

    private AdapterUtil() {
    }
}
