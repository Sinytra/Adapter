package org.sinytra.adapter.edit;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.ValueSource;
import org.sinytra.adapter.model.constant.MixinAnnotations;
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

        String name = handler.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
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
        if (plan.receiver() != null) {
            stack += load(gen, plan.receiver(), paramIndices);
            Type ownerType = Type.getObjectType(handlerOwner.name);
            if (!params.get(argIndex(plan.receiver(), paramIndices)).equals(ownerType)) {
                gen.checkCast(ownerType);
            }
        }
        for (ValueSource source : plan.params()) {
            stack += load(gen, source, paramIndices);
        }
        invokeHandler(gen, handler, handlerOwner);
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        mirror.maxStack = Math.max(stack, Type.getReturnType(handler.desc).getSize());
        mirror.maxLocals = (Type.getArgumentsAndReturnSizes(desc) >> 2) - (isStatic ? 1 : 0);

        handler.access = OpcodeUtil.setAccessVisibility(handler.access, Opcodes.ACC_PUBLIC);
        handler.visibleAnnotations.remove(context.methodAnnotation().unwrap());
        handler.visitAnnotation(MixinAnnotations.UNIQUE, true);

        ClassNode generatedTarget = getGeneratedTarget(context, destinationClass);
        synchronized (generatedTarget) {
            generatedTarget.methods.add(mirror);
        }
        return mirror;
    }

    public static void generateMove(MixinContext context, String destinationClass, RelocationPlan.Move plan) {
        ClassNode mixin = context.classNode();
        ClassNode generatedTarget = getGeneratedTarget(context, destinationClass);
        List<MethodNode> moved = new ArrayList<>(plan.lambdas());
        moved.addFirst(context.methodNode());

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

    public static void invokeHandler(GeneratorAdapter gen, MethodNode handler, ClassNode handlerOwner) {
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
