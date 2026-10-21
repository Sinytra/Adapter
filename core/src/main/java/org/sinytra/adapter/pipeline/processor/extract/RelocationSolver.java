package org.sinytra.adapter.pipeline.processor.extract;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.RelocationPlan;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ValueSource;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class RelocationSolver {
    public sealed interface Result {
        record Solved(RelocationPlan plan) implements Result {
        }

        record Unbound(String reason) implements Result {
        }
    }

    public static Result solve(MixinContext context, Recipe recipe, Configuration dirty) {
        List<Parameter> params = Parameters.parse(context.methodNode());
        if (context.locals().capturesLocals()) {
            return new Result.Unbound("the handler captures locals of the original target");
        }

        String destination = dirty.getTargetClass();
        RelocationPlan.Move move = findMove(context, recipe, destination);
        if (move != null) {
            return new Result.Solved(move);
        }

        Relocation relocation = dirty.getProperty(IntentKeys.RELOCATION).orElse(null);
        if (relocation == null) {
            return new Result.Unbound("the handler uses its own class, and nothing at the new target holds its instance");
        }
        MethodQualifier targetMethod = dirty.getTargetMethod();
        TargetPair newTarget = targetMethod != null
            ? context.dirtyMethods().resolveDeclared(targetMethod.withOwner(Type.getObjectType(destination)))
            : null;
        if (newTarget == null) {
            return new Result.Unbound("the new target method doesn't exist");
        }

        TargetPair handlerTarget = recipe.getNewCleanTarget();
        if (handlerTarget == null) {
            return new Result.Unbound("the original target method doesn't exist");
        }
        boolean isStatic = MethodAnalyzer.isStatic(newTarget.methodNode());
        if (params.stream().anyMatch(Parameter::isLocalOrShare)) {
            return new Result.Unbound("the handler has @Local or @Share parameters, which the generated injector can't pass on");
        }

        List<ValueSource> forwarded = IntStream.range(0, params.size()).<ValueSource>mapToObj(ValueSource.Forwarded::new).toList();
        if (context.isStatic()) {
            return new Result.Solved(new RelocationPlan.Mirror(handlerTarget.classNode(), isStatic, null, forwarded));
        }
        ValueSource receiver = findReceiver(relocation, newTarget);
        if (receiver == null) {
            return new Result.Unbound("nothing at the new target holds the original instance");
        }
        return new Result.Solved(new RelocationPlan.Mirror(handlerTarget.classNode(), isStatic, receiver, forwarded));
    }

    @Nullable
    private static RelocationPlan.Move findMove(MixinContext context, Recipe recipe, String destination) {
        ClassNode mixin = context.classNode();
        MethodNode handler = context.methodNode();
        TargetPair cleanTarget = recipe.getCleanTarget();
        String origin = cleanTarget != null ? cleanTarget.classNode().name : destination;
        boolean inherited = context.environment().inheritanceHandler(context.dirtyLookup()).isClassInherited(destination, origin);

        List<MethodNode> lambdas = MethodAnalyzer.findOwnIndyHandles(mixin, handler).stream()
            .filter(lambda -> lambda.handle().getName().startsWith(MethodAnalyzer.LAMBDA_PREFIX + handler.name))
            .map(MethodAnalyzer.IndyHandle::method)
            .toList();
        List<MethodNode> moved = new ArrayList<>(lambdas);
        moved.addFirst(handler);

        List<FieldNode> widenedFields = new ArrayList<>();
        List<MethodNode> widenedMethods = new ArrayList<>();
        for (MethodNode method : moved) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode fieldInsn && fieldInsn.owner.equals(mixin.name)) {
                    FieldNode field = AdapterUtil.findOne(mixin.fields, f -> f.name.equals(fieldInsn.name)).orElse(null);
                    if (field == null || !isAvailable(field.visibleAnnotations, inherited)) {
                        return null;
                    }
                    addWidened(widenedFields, field, field.visibleAnnotations);
                } else if (insn instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(mixin.name)) {
                    MethodNode member = AdapterUtil.findMethod(mixin, methodInsn.name, methodInsn.desc);
                    if (member == null || !isAvailable(member.visibleAnnotations, inherited)) {
                        return null;
                    }
                    addWidened(widenedMethods, member, member.visibleAnnotations);
                }
            }
        }
        return new RelocationPlan.Move(lambdas, widenedFields, widenedMethods);
    }

    private static boolean isAvailable(@Nullable List<AnnotationNode> annotations, boolean inherited) {
        return inherited || AdapterUtil.hasAnnotation(annotations, MixinAnnotations.SHADOW);
    }

    private static <T> void addWidened(List<T> widened, T member, @Nullable List<AnnotationNode> annotations) {
        if (!AdapterUtil.hasAnnotation(annotations, MixinAnnotations.SHADOW) && !widened.contains(member)) {
            widened.add(member);
        }
    }

    @Nullable
    private static ValueSource findReceiver(Relocation relocation, TargetPair newTarget) {
        if (relocation instanceof Relocation.ReceivesOrigin(int argIndex)) {
            Type[] args = Type.getArgumentTypes(newTarget.methodNode().desc);
            if (argIndex >= args.length) {
                return null;
            }
            int slot = MethodAnalyzer.isStatic(newTarget.methodNode()) ? 0 : 1;
            for (int i = 0; i < argIndex; i++) {
                slot += args[i].getSize();
            }
            return new ValueSource.Captured(args[argIndex], slot, true);
        }
        return null;
    }

    private RelocationSolver() {
    }
}
