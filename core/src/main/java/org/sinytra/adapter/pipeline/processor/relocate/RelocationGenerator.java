package org.sinytra.adapter.pipeline.processor.relocate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ValueSource;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_ARGS_ONLY;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_INDEX;

public final class RelocationGenerator {
    public static MethodNode generateMirror(MixinContext context, String destinationClass, RelocationPlan.Mirror plan) {
        MethodNode handler = context.methodNode();
        ClassNode handlerOwner = plan.handlerOwner();
        boolean isStatic = plan.isStatic();
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);

        int forwardedCount = (int) plan.params().stream().filter(ValueSource.Forwarded.class::isInstance).count();
        List<Type> params = new ArrayList<>();
        for (int i = 0; i < forwardedCount; i++) {
            params.add(null);
        }
        for (int i = 0; i < plan.params().size(); i++) {
            if (plan.params().get(i) instanceof ValueSource.Forwarded(int param)) {
                params.set(param, handlerArgs[i]);
            }
        }

        Map<ValueSource, Integer> paramIndices = new HashMap<>();
        List<ValueSource.Captured> captured = new ArrayList<>();
        List<ValueSource> sources = new ArrayList<>();
        if (plan.receiver() != null) {
            sources.add(plan.receiver());
        }
        sources.addAll(plan.params());
        for (ValueSource source : sources) {
            if (source instanceof ValueSource.Captured capture && !paramIndices.containsKey(capture)) {
                paramIndices.put(capture, params.size());
                params.add(capture.type());
                captured.add(capture);
            }
        }

        String name = handler.name + "$adapter$mirror$" + simpleName(context.classNode());
        String desc = Type.getMethodDescriptor(Type.getReturnType(handler.desc), params.toArray(Type[]::new));
        MethodNode mirror = new MethodNode(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), name, desc, null, null);
        mirror.visibleAnnotations = new ArrayList<>(handler.visibleAnnotations);
        copyParameterAnnotations(handler, mirror, plan.params());
        for (ValueSource.Captured capture : captured) {
            AnnotationVisitor local = mirror.visitParameterAnnotation(paramIndices.get(capture), MixinAnnotations.LOCAL, false);
            local.visit(PROPERTY_ARGS_ONLY, capture.argsOnly());
            local.visit(PROPERTY_INDEX, capture.slot());
        }

        GeneratorAdapter gen = new GeneratorAdapter(mirror, mirror.access, mirror.name, mirror.desc);
        gen.newLabel();
        int stack = 0;
        int guardStack = 0;
        if (plan.receiver() != null && plan.guard() != null) {
            String active = generateGuard(context, plan.guard());
            Label run = new Label();
            guardStack = loadReceiver(gen, plan.receiver(), paramIndices, params, handlerOwner);
            gen.visitMethodInsn(Opcodes.INVOKEVIRTUAL, handlerOwner.name, active, "()Z", false);
            gen.visitJumpInsn(Opcodes.IFNE, run);
            switch (plan.guard().passthrough()) {
                case SKIP -> {
                }
                case KEEP_VALUE -> gen.loadArg(0);
                case PROCEED -> gen.push(true);
                case CALL_ORIGINAL -> guardStack = Math.max(guardStack, callOriginal(gen, params));
            }
            gen.returnValue();
            gen.visitLabel(run);
        }
        if (plan.receiver() != null) {
            stack += loadReceiver(gen, plan.receiver(), paramIndices, params, handlerOwner);
        }
        for (ValueSource source : plan.params()) {
            stack += load(gen, source, paramIndices);
        }
        invokeHandler(gen, handler, handlerOwner);
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        mirror.maxStack = Math.max(Math.max(stack, guardStack), Type.getReturnType(handler.desc).getSize());
        mirror.maxLocals = (Type.getArgumentsAndReturnSizes(desc) >> 2) - (isStatic ? 1 : 0);

        handler.access = OpcodeUtil.setAccessVisibility(handler.access, Opcodes.ACC_PUBLIC);
        handler.visibleAnnotations.remove(context.methodAnnotation().unwrap());
        handler.visitAnnotation(MixinAnnotations.UNIQUE, true);

        ClassNode generatedTarget = getGeneratedTarget(context, destinationClass);
        synchronized (generatedTarget) {
            mirror.name = uniqueMethodName(generatedTarget, mirror.name);
            generatedTarget.methods.add(mirror);
        }
        context.patchContext().onRollback(() -> {
            synchronized (generatedTarget) {
                generatedTarget.methods.remove(mirror);
            }
        });
        return mirror;
    }

    private static int loadReceiver(GeneratorAdapter gen, ValueSource receiver, Map<ValueSource, Integer> paramIndices, List<Type> params, ClassNode handlerOwner) {
        int size = load(gen, receiver, paramIndices);
        Type ownerType = Type.getObjectType(handlerOwner.name);
        if (!params.get(argIndex(receiver, paramIndices)).equals(ownerType)) {
            gen.checkCast(ownerType);
        }
        return size;
    }

    private static int callOriginal(GeneratorAdapter gen, List<Type> params) {
        int operation = params.indexOf(TypeConstants.OPERATION_TYPE);
        gen.loadArg(operation);
        gen.push(operation);
        gen.newArray(TypeConstants.OBJECT_TYPE);
        int operandSize = 1;
        for (int i = 0; i < operation; i++) {
            gen.dup();
            gen.push(i);
            gen.loadArg(i);
            gen.box(params.get(i));
            gen.arrayStore(TypeConstants.OBJECT_TYPE);
            operandSize = Math.max(operandSize, params.get(i).getSize());
        }
        gen.invokeInterface(TypeConstants.OPERATION_TYPE, new Method("call", TypeConstants.OBJECT_TYPE, new Type[]{Type.getType(Object[].class)}));
        Type returnType = gen.getReturnType();
        if (returnType == Type.VOID_TYPE) {
            gen.pop();
        } else {
            gen.unbox(returnType);
        }
        return 4 + operandSize + 2;
    }

    private static String generateGuard(MixinContext context, RelocationPlan.Guard guard) {
        TargetPair origin = guard.via().method();
        MethodInsnNode call = guard.via().call();
        int ordinal = 0;
        for (AbstractInsnNode insn = origin.methodNode().instructions.getFirst(); insn != call; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode other && other.owner.equals(call.owner) && other.name.equals(call.name) && other.desc.equals(call.desc)) {
                ordinal++;
            }
        }

        ClassNode generatedTarget = getGeneratedTarget(context, origin.classNode().name);
        String base = "adapter$via$" + simpleName(context.classNode()) + "$" + context.methodNode().name;
        FieldNode depth;
        List<MethodNode> methods;
        synchronized (generatedTarget) {
            String name = base;
            for (int i = 1; hasMethodNamed(generatedTarget, name) || hasFieldNamed(generatedTarget, name); i++) {
                name = base + "$" + i;
            }
            depth = new FieldNode(Opcodes.ACC_PRIVATE, name, Type.INT_TYPE.getDescriptor(), null, null);
            methods = List.of(
                generateIsActive(generatedTarget, depth),
                generateDepthChange(origin, call, ordinal, generatedTarget, depth, 1),
                generateDepthChange(origin, call, ordinal, generatedTarget, depth, -1)
            );
            generatedTarget.fields.add(depth);
            generatedTarget.methods.addAll(methods);
        }
        context.patchContext().onRollback(() -> {
            synchronized (generatedTarget) {
                generatedTarget.fields.remove(depth);
                generatedTarget.methods.removeAll(methods);
            }
        });
        return depth.name;
    }

    private static MethodNode generateIsActive(ClassNode generatedTarget, FieldNode depth) {
        MethodNode active = new MethodNode(Opcodes.ACC_PUBLIC, depth.name, "()Z", null, null);
        GeneratorAdapter gen = new GeneratorAdapter(active, active.access, active.name, active.desc);
        Label inactive = new Label();
        gen.loadThis();
        gen.getField(Type.getObjectType(generatedTarget.name), depth.name, Type.INT_TYPE);
        gen.visitJumpInsn(Opcodes.IFLE, inactive);
        gen.push(true);
        gen.returnValue();
        gen.visitLabel(inactive);
        gen.push(false);
        gen.returnValue();
        gen.endMethod();
        active.maxStack = 1;
        active.maxLocals = 1;
        return active;
    }

    private static MethodNode generateDepthChange(TargetPair origin, MethodInsnNode call, int ordinal, ClassNode generatedTarget, FieldNode depth, int change) {
        Type callback = Type.getReturnType(origin.methodNode().desc) == Type.VOID_TYPE ? TypeConstants.CI_TYPE : TypeConstants.CIR_TYPE;
        String name = depth.name + (change > 0 ? "$enter" : "$exit");
        MethodNode inject = new MethodNode(Opcodes.ACC_PRIVATE, name, Type.getMethodDescriptor(Type.VOID_TYPE, callback), null, null);
        AnnotationVisitor annotation = inject.visitAnnotation(MixinAnnotations.INJECT, true);
        AnnotationVisitor method = annotation.visitArray("method");
        method.visit(null, origin.methodNode().name + origin.methodNode().desc);
        method.visitEnd();
        AnnotationVisitor ats = annotation.visitArray("at");
        AnnotationVisitor at = ats.visitAnnotation(null, MixinAnnotations.AT);
        at.visit("value", "INVOKE");
        at.visit("target", Type.getObjectType(call.owner).getDescriptor() + call.name + call.desc);
        at.visit("ordinal", ordinal);
        if (change < 0) {
            at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER");
        }
        at.visitEnd();
        ats.visitEnd();
        annotation.visitEnd();

        Type owner = Type.getObjectType(generatedTarget.name);
        GeneratorAdapter gen = new GeneratorAdapter(inject, inject.access, inject.name, inject.desc);
        gen.loadThis();
        gen.dup();
        gen.getField(owner, depth.name, Type.INT_TYPE);
        gen.push(change);
        gen.math(GeneratorAdapter.ADD, Type.INT_TYPE);
        gen.putField(owner, depth.name, Type.INT_TYPE);
        gen.returnValue();
        gen.endMethod();
        inject.maxStack = 3;
        inject.maxLocals = 2;
        return inject;
    }

    public static void generateMove(MixinContext context, String destinationClass, RelocationPlan.Move plan) {
        ClassNode mixin = context.classNode();
        ClassNode generatedTarget = getGeneratedTarget(context, destinationClass);
        List<MethodNode> moved = new ArrayList<>(plan.lambdas());
        moved.addFirst(context.methodNode());
        registerMoveRollback(context, generatedTarget, plan, moved);

        if (plan.makeStatic()) {
            AdapterUtil.makeStatic(context.methodNode());
        }
        if (MethodAnalyzer.isStatic(context.methodNode()) && (context.methodNode().access & Opcodes.ACC_SYNTHETIC) == 0) {
            context.methodNode().access = OpcodeUtil.setAccessVisibility(context.methodNode().access, Opcodes.ACC_PRIVATE);
        }
        plan.widenedFields().forEach(field -> field.access = widenAccess(field.access));
        plan.widenedMethods().forEach(method -> method.access = widenAccess(method.access));
        for (MethodNode method : moved) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(mixin.name)) {
                    methodInsn.owner = destinationClass;
                } else if (insn instanceof FieldInsnNode fieldInsn && fieldInsn.owner.equals(mixin.name)) {
                    fieldInsn.owner = destinationClass;
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    retargetLambdas(indy, mixin, plan.lambdas(), generatedTarget);
                }
            }
            if (!MethodAnalyzer.isStatic(method) && method.localVariables != null) {
                method.localVariables.stream()
                    .filter(local -> local.index == 0)
                    .findFirst()
                    .ifPresent(local -> local.desc = Type.getObjectType(generatedTarget.name).getDescriptor());
            }
        }

        synchronized (generatedTarget) {
            generatedTarget.methods.addAll(moved);
        }
        context.patchContext().postApply(() -> mixin.methods.removeAll(moved));
    }

    public static void generateCancellers(MixinContext context, ClassNode targetClass, List<MethodNode> laterPieces) {
        MethodNode handler = context.methodNode();
        boolean isStatic = context.isStatic();
        ClassNode generatedTarget = getGeneratedTarget(context, targetClass.name);
        FieldNode tracker = new FieldNode(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0),
            "adapter$cancelled$" + simpleName(context.classNode()) + "$" + handler.name, Type.BOOLEAN_TYPE.getDescriptor(), null, null);
        List<MethodNode> cancellers = new ArrayList<>();
        synchronized (generatedTarget) {
            String base = tracker.name;
            for (int i = 1; generatedTarget.fields.stream().anyMatch(f -> f.name.equals(tracker.name)); i++) {
                tracker.name = base + "$" + i;
            }
            generatedTarget.fields.add(tracker);
            for (int i = 0; i < laterPieces.size(); i++) {
                MethodNode canceller = generateCanceller(generatedTarget, tracker, handler, laterPieces.get(i), isStatic, i == laterPieces.size() - 1);
                canceller.name = uniqueMethodName(generatedTarget, canceller.name);
                generatedTarget.methods.add(canceller);
                cancellers.add(canceller);
            }
        }
        context.patchContext().onRollback(() -> {
            synchronized (generatedTarget) {
                generatedTarget.fields.remove(tracker);
                generatedTarget.methods.removeAll(cancellers);
            }
        });

        MethodQualifier cancel = new MethodQualifier(TypeConstants.CI_TYPE.getDescriptor(), "cancel", "()V");
        for (MethodInsnNode call : MethodAnalyzer.findCalls(handler, cancel)) {
            InsnList set = new InsnList();
            if (!isStatic) {
                set.add(new VarInsnNode(Opcodes.ALOAD, 0));
            }
            set.add(new InsnNode(Opcodes.ICONST_1));
            set.add(new FieldInsnNode(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, targetClass.name, tracker.name, tracker.desc));
            handler.instructions.insertBefore(call, set);
        }
    }

    private static MethodNode generateCanceller(ClassNode generatedTarget, FieldNode tracker, MethodNode handler, MethodNode piece, boolean isStatic, boolean last) {
        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, TypeConstants.CI_TYPE);
        MethodNode canceller = new MethodNode(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0), handler.name + "$adapter$canceller$" + piece.name, desc, null, null);
        AnnotationVisitor inject = canceller.visitAnnotation(MixinAnnotations.INJECT, true);
        AnnotationVisitor method = inject.visitArray("method");
        method.visit(null, piece.name + piece.desc);
        method.visitEnd();
        AnnotationVisitor at = inject.visitArray("at");
        AnnotationVisitor head = at.visitAnnotation(null, MixinAnnotations.AT);
        head.visit("value", "HEAD");
        head.visitEnd();
        at.visitEnd();
        inject.visit("cancellable", Boolean.TRUE);
        inject.visitEnd();

        Type owner = Type.getObjectType(generatedTarget.name);
        GeneratorAdapter gen = new GeneratorAdapter(canceller, canceller.access, canceller.name, canceller.desc);
        Label end = new Label();
        gen.newLabel();
        if (isStatic) {
            gen.getStatic(owner, tracker.name, Type.BOOLEAN_TYPE);
        } else {
            gen.loadThis();
            gen.getField(owner, tracker.name, Type.BOOLEAN_TYPE);
        }
        gen.visitJumpInsn(Opcodes.IFEQ, end);
        if (last) {
            gen.newLabel();
            if (isStatic) {
                gen.push(false);
                gen.putStatic(owner, tracker.name, Type.BOOLEAN_TYPE);
            } else {
                gen.loadThis();
                gen.push(false);
                gen.putField(owner, tracker.name, Type.BOOLEAN_TYPE);
            }
        }
        gen.newLabel();
        gen.loadArg(0);
        gen.invokeVirtual(TypeConstants.CI_TYPE, new Method("cancel", "()V"));
        gen.visitLabel(end);
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        canceller.maxStack = 2;
        canceller.maxLocals = isStatic ? 1 : 2;
        return canceller;
    }

    private static String uniqueMethodName(ClassNode generatedTarget, String name) {
        String unique = name;
        for (int i = 1; hasMethodNamed(generatedTarget, unique); i++) {
            unique = name + "$" + i;
        }
        return unique;
    }

    private static boolean hasMethodNamed(ClassNode cls, String name) {
        return cls.methods.stream().anyMatch(m -> m.name.equals(name));
    }

    private static boolean hasFieldNamed(ClassNode cls, String name) {
        return cls.fields.stream().anyMatch(f -> f.name.equals(name));
    }

    private static String simpleName(ClassNode cls) {
        return cls.name.substring(cls.name.lastIndexOf('/') + 1);
    }

    private static void registerMoveRollback(MixinContext context, ClassNode generatedTarget, RelocationPlan.Move plan, List<MethodNode> moved) {
        ClassNode mixin = context.classNode();
        int[] fieldAccess = plan.widenedFields().stream().mapToInt(field -> field.access).toArray();
        int[] methodAccess = plan.widenedMethods().stream().mapToInt(method -> method.access).toArray();
        List<MethodNode> lambdaCopies = plan.lambdas().stream().map(AdapterUtil::copyMethod).toList();
        context.patchContext().onRollback(() -> {
            synchronized (generatedTarget) {
                generatedTarget.methods.removeAll(moved);
            }
            for (int i = 0; i < fieldAccess.length; i++) {
                plan.widenedFields().get(i).access = fieldAccess[i];
            }
            for (int i = 0; i < methodAccess.length; i++) {
                plan.widenedMethods().get(i).access = methodAccess[i];
            }
            for (int i = 0; i < lambdaCopies.size(); i++) {
                int index = mixin.methods.indexOf(plan.lambdas().get(i));
                if (index != -1) {
                    mixin.methods.set(index, lambdaCopies.get(i));
                }
            }
        });
    }

    private static ClassNode getGeneratedTarget(MixinContext context, String destinationClass) {
        PatchEnvironment environment = context.environment();
        String parent = environment.dirtyClassLookup().getClass(destinationClass).map(cls -> cls.superName).orElse(null);
        ClassNode generatedTarget = environment.classGenerator().getOrGenerateMixinClass(context.classNode(), destinationClass, parent);
        environment.refmapHolder().copyEntries(context.classNode().name, generatedTarget.name);
        return generatedTarget;
    }

    private static void retargetLambdas(InvokeDynamicInsnNode indy, ClassNode mixin, List<MethodNode> lambdas, ClassNode generatedTarget) {
        for (int i = 0; i < indy.bsmArgs.length; i++) {
            if (indy.bsmArgs[i] instanceof Handle handle && handle.getOwner().equals(mixin.name)
                && lambdas.stream().anyMatch(lambda -> lambda.name.equals(handle.getName()) && lambda.desc.equals(handle.getDesc()))
            ) {
                indy.bsmArgs[i] = new Handle(handle.getTag(), generatedTarget.name, handle.getName(), handle.getDesc(), handle.isInterface());
            }
        }
    }

    private static int widenAccess(int access) {
        int visibility = OpcodeUtil.getAccessVisibility(access);
        if (visibility == Opcodes.ACC_PRIVATE || visibility == 0) {
            return OpcodeUtil.setAccessVisibility(access, Opcodes.ACC_PUBLIC) | Opcodes.ACC_SYNTHETIC;
        }
        return access;
    }

    private static void invokeHandler(GeneratorAdapter gen, MethodNode handler, ClassNode handlerOwner) {
        boolean isInterface = (handlerOwner.access & Opcodes.ACC_INTERFACE) != 0;
        int opcode = MethodAnalyzer.isStatic(handler) ? Opcodes.INVOKESTATIC : isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        gen.visitMethodInsn(opcode, handlerOwner.name, handler.name, handler.desc, isInterface);
    }

    private static int load(GeneratorAdapter gen, ValueSource source, Map<ValueSource, Integer> paramIndices) {
        int arg = argIndex(source, paramIndices);
        gen.loadArg(arg);
        return gen.getArgumentTypes()[arg].getSize();
    }

    private static int argIndex(ValueSource source, Map<ValueSource, Integer> paramIndices) {
        return switch (source) {
            case ValueSource.Forwarded(int param) -> param;
            case ValueSource.Captured capture -> paramIndices.get(capture);
        };
    }

    private static void copyParameterAnnotations(MethodNode handler, MethodNode mirror, List<ValueSource> sources) {
        int count = Type.getArgumentCount(mirror.desc);
        mirror.visibleParameterAnnotations = copyParameterAnnotations(handler.visibleParameterAnnotations, sources, count);
        mirror.invisibleParameterAnnotations = copyParameterAnnotations(handler.invisibleParameterAnnotations, sources, count);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static List<AnnotationNode>[] copyParameterAnnotations(@Nullable List<AnnotationNode>[] annotations, List<ValueSource> sources, int count) {
        if (annotations == null) {
            return null;
        }
        List<AnnotationNode>[] copy = new List[count];
        for (int i = 0; i < sources.size() && i < annotations.length; i++) {
            if (sources.get(i) instanceof ValueSource.Forwarded(int param) && annotations[i] != null) {
                copy[param] = new ArrayList<>(annotations[i]);
            }
        }
        return copy;
    }

    private RelocationGenerator() {
    }
}
