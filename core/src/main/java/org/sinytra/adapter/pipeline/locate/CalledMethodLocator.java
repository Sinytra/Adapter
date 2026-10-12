package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.flow.DataFlow.ImplicitCalls;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer.IndyHandle;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;
import java.util.function.Predicate;

/// Finds the injection point in a method the dirty target calls: a helper or lambda the code moved into, or another class it was extracted to.
/// The first call must be new in the dirty target. The nearest methods with a match win.
public final class CalledMethodLocator implements Locator {
    private static final int MAX_DEPTH = 3;
    private static final List<String> IGNORED_PACKAGES = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/");

    /// A method reached from the dirty target
    /// @param originLocals the local slots that hold the dirty target's instance
    private record Callee(TargetPair method, Set<Integer> originLocals) {
    }

    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn) {
        AtData cleanAt = recipe.clean().getAtData();
        if (cleanAt == null) {
            return List.of();
        }

        TargetPair origin = targets.dirty();
        Set<MethodNode> visited = new HashSet<>();
        visited.add(origin.methodNode());
        Set<MethodInsnNode> newCalls = new HashSet<>(MethodAnalyzer.findDirtyOnlyCalls(targets.clean().methodNode(), origin.methodNode(), false));
        Callee start = new Callee(origin, context.isStatic() ? Set.of() : Set.of(0));

        List<Callee> frontier = findCallees(context, start, newCalls::contains, visited);
        for (int depth = 1; !frontier.isEmpty(); depth++) {
            List<Candidate> candidates = new ArrayList<>();
            for (Callee callee : frontier) {
                candidates.addAll(findIn(context, targets, cleanAt, cleanInsn, callee));
            }
            if (!candidates.isEmpty() || depth == MAX_DEPTH) {
                return candidates;
            }

            List<Callee> next = new ArrayList<>();
            for (Callee callee : frontier) {
                next.addAll(findCallees(context, callee, call -> true, visited));
            }
            frontier = next;
        }
        return List.of();
    }

    /// {@return the instructions the clean `@At` selects in the callee, matched to the clean instruction where the alignment can}
    private static List<Candidate> findIn(MixinContext context, Targets targets, AtData cleanAt, AbstractInsnNode cleanInsn, Callee callee) {
        List<AbstractInsnNode> selected = context.injections().find(callee.method(), InjectionQuery.of(cleanAt.withOrdinal(null)));
        if (selected.isEmpty()) {
            return List.of();
        }

        Relocation relocation = relocation(targets.dirty(), callee);
        List<Candidate> aligned = MethodAlignment.align(targets.clean().methodNode(), callee.method().methodNode()).find(cleanInsn).stream()
            .filter(match -> selected.contains(match.dirty()))
            .map(match -> new Candidate(callee.method(), match.dirty(), match.confidence(), relocation))
            .toList();
        if (!aligned.isEmpty()) {
            return aligned;
        }
        return selected.stream()
            .map(insn -> new Candidate(callee.method(), insn, MethodAlignment.Confidence.POSITIONAL, relocation))
            .toList();
    }

    /// {@return how a handler moved into the callee's class reaches the origin instance, or null if it stays in the origin class or can't}
    @Nullable
    private static Relocation relocation(TargetPair origin, Callee callee) {
        MethodNode method = callee.method().methodNode();
        if (callee.method().classNode().name.equals(origin.classNode().name) || !MethodAnalyzer.isStatic(method)) {
            return null;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        int slot = 0;
        for (int i = 0; i < args.length; i++) {
            if (callee.originLocals().contains(slot)) {
                return new Relocation.ReceivesOrigin(i);
            }
            slot += args[i].getSize();
        }
        return null;
    }

    /// {@return the methods the caller calls at the top level and the lambdas it creates, that weren't visited yet}
    /// When the handler is an instance method, a callee must receive the origin instance.
    private static List<Callee> findCallees(MixinContext context, Callee caller, Predicate<MethodInsnNode> filter, Set<MethodNode> visited) {
        TargetPair from = caller.method();
        DataFlow flow = context.dataFlow(from);
        List<Callee> callees = new ArrayList<>();

        for (MethodInsnNode call : flow.findTopLevelCalls(ImplicitCalls.UNWRAP)) {
            if (!filter.test(call)
                || IGNORED_PACKAGES.stream().anyMatch(call.owner::startsWith)
                || !context.patchContext().environment().isKnownPackage(AdapterUtil.internalNameToPkg(call.owner))
            ) {
                continue;
            }
            TargetPair method = context.dirtyMethods().resolveInherited(MethodQualifier.create(call));
            if (method != null && !visited.contains(method.methodNode())) {
                addCallee(context, callees, visited, method, passedLocals(flow, call, call.getOpcode() != Opcodes.INVOKESTATIC, call.desc, caller.originLocals()));
            }
        }

        for (IndyHandle lambda : MethodAnalyzer.findOwnIndyHandles(from.classNode(), from.methodNode())) {
            if (!visited.contains(lambda.method())) {
                Handle handle = lambda.handle();
                Set<Integer> locals = passedLocals(flow, lambda.indy(), handle.getTag() != Opcodes.H_INVOKESTATIC, handle.getDesc(), caller.originLocals());
                addCallee(context, callees, visited, new TargetPair(from.classNode(), lambda.method()), locals);
            }
        }
        return callees;
    }

    private static void addCallee(MixinContext context, List<Callee> callees, Set<MethodNode> visited, TargetPair method, Set<Integer> originLocals) {
        if (context.isStatic() || !originLocals.isEmpty()) {
            visited.add(method.methodNode());
            callees.add(new Callee(method, originLocals));
        }
    }

    /// {@return the callee's local slots that receive one of the caller's tracked locals}
    /// The operands are the receiver, if any, followed by the leading arguments of the callee descriptor. Lambdas only take their captured arguments.
    private static Set<Integer> passedLocals(DataFlow flow, AbstractInsnNode insn, boolean hasReceiver, String calleeDesc, Set<Integer> tracked) {
        if (tracked.isEmpty()) {
            return Set.of();
        }
        List<SourceValue> operands = flow.operands(insn);
        Type[] args = Type.getArgumentTypes(calleeDesc);
        Set<Integer> passed = new HashSet<>();
        int slot = 0;
        for (int i = 0; i < operands.size(); i++) {
            int argIndex = hasReceiver ? i - 1 : i;
            if (argIndex >= args.length) {
                break;
            }
            if (isTrackedLoad(operands.get(i), tracked)) {
                passed.add(slot);
            }
            slot += argIndex < 0 ? 1 : args[argIndex].getSize();
        }
        return passed;
    }

    private static boolean isTrackedLoad(SourceValue value, Set<Integer> tracked) {
        return value.insns.size() == 1 && value.insns.iterator().next() instanceof VarInsnNode load
            && load.getOpcode() == Opcodes.ALOAD && tracked.contains(load.var);
    }
}
