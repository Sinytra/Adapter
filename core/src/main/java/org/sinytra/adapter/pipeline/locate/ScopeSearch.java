package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.flow.DataFlow.ImplicitCalls;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer.IndyHandle;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;
import java.util.function.Predicate;

public final class ScopeSearch {
    public enum Reach {
        TARGET,
        CALLS
    }

    private static final int MAX_DEPTH = 3;
    private static final List<String> IGNORED_PACKAGES = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/");

    private record Callee(TargetPair root, TargetPair method, Set<Integer> originLocals, @Nullable MethodInsnNode calledFrom, @Nullable MethodInsnNode rootCall) {
    }

    private final MixinContext context;
    @Nullable
    private final TargetPair cleanTarget;
    private final List<List<Scope>> tiers = new ArrayList<>();
    private final Set<MethodNode> visited = new HashSet<>();
    private final int maxTier;
    private List<Callee> frontier;

    private ScopeSearch(MixinContext context, @Nullable TargetPair cleanTarget, List<Scope> same, List<Scope> replacements, Reach reach) {
        this.context = context;
        this.cleanTarget = cleanTarget;
        this.tiers.add(same);
        this.tiers.add(replacements);
        this.maxTier = reach == Reach.CALLS ? 1 + MAX_DEPTH : 1;
        same.forEach(scope -> this.visited.add(scope.method().methodNode()));
        replacements.forEach(scope -> this.visited.add(scope.method().methodNode()));
    }

    public static ScopeSearch of(MixinContext context, Recipe recipe, Reach reach) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) {
            return new ScopeSearch(context, null, List.of(), List.of(), Reach.TARGET);
        }
        TargetPair same = recipe.getNewCleanTarget();
        List<Scope> sameScopes = same != null ? List.of(Scope.of(new Targets(cleanTarget, same))) : List.of();
        MethodQualifier qualifier = recipe.clean().getTargetMethod();
        List<Scope> replacements = qualifier == null ? List.of() : OriginLocator.locate(context, cleanTarget, recipe.clean().getTargetClass(), qualifier).stream()
            .filter(origin -> same == null || !origin.method().isSameMethod(same))
            .map(origin -> new Scope(new Targets(cleanTarget, origin.method()), Scope.Kind.REPLACEMENT, 0, origin.alignment(), null))
            .toList();
        return new ScopeSearch(context, cleanTarget, sameScopes, replacements, reach);
    }

    public static ScopeSearch of(MixinContext context, Scope scope) {
        return new ScopeSearch(context, scope.targets().clean(), List.of(scope), List.of(), Reach.TARGET);
    }

    public int tierCount() {
        return this.maxTier + 1;
    }

    public List<Scope> tier(int tier) {
        while (this.tiers.size() <= tier && this.tiers.size() <= this.maxTier) {
            this.tiers.add(nextCalledTier());
        }
        return tier < this.tiers.size() ? this.tiers.get(tier) : List.of();
    }

    private List<Scope> nextCalledTier() {
        int depth = this.tiers.size() - 1;
        if (this.frontier == null) {
            this.frontier = new ArrayList<>();
            for (Scope root : this.tiers.get(0)) {
                this.frontier.addAll(findRootCallees(root));
            }
            for (Scope root : this.tiers.get(1)) {
                this.frontier.addAll(findRootCallees(root));
            }
        } else {
            List<Callee> next = new ArrayList<>();
            for (Callee callee : this.frontier) {
                next.addAll(findCallees(callee, call -> true, false));
            }
            this.frontier = next;
        }
        return this.frontier.stream()
            .map(callee -> new Scope(new Targets(this.cleanTarget, callee.method()), Scope.Kind.CALLED, depth,
                MethodAlignment.align(this.cleanTarget.methodNode(), callee.method().methodNode()), relocation(callee)))
            .toList();
    }

    private List<Callee> findRootCallees(Scope root) {
        Set<MethodInsnNode> newCalls = new HashSet<>(MethodAnalyzer.findDirtyOnlyCalls(this.cleanTarget.methodNode(), root.method().methodNode(), false));
        Callee start = new Callee(root.method(), root.method(), this.context.isStatic() ? Set.of() : Set.of(0), null, null);
        return findCallees(start, newCalls::contains, true);
    }

    @Nullable
    private static Relocation relocation(Callee callee) {
        MethodNode method = callee.method().methodNode();
        boolean isStatic = MethodAnalyzer.isStatic(method);
        if (callee.method().classNode().name.equals(callee.root().classNode().name) || !isStatic && callee.originLocals().contains(0)) {
            return null;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < args.length; i++) {
            if (callee.originLocals().contains(slot)) {
                Relocation.OriginCall via = callee.rootCall() != null ? new Relocation.OriginCall(callee.root(), callee.rootCall()) : null;
                return new Relocation.ReceivesOrigin(i, via);
            }
            slot += args[i].getSize();
        }
        return callee.calledFrom() != null ? new Relocation.CalledFrom() : null;
    }

    private List<Callee> findCallees(Callee caller, Predicate<MethodInsnNode> filter, boolean fromRoot) {
        TargetPair from = caller.method();
        DataFlow flow = this.context.dataFlow(from);
        List<Callee> callees = new ArrayList<>();

        for (MethodInsnNode call : flow.findTopLevelCalls(ImplicitCalls.UNWRAP)) {
            if (!filter.test(call)
                || IGNORED_PACKAGES.stream().anyMatch(call.owner::startsWith)
                || !this.context.patchContext().environment().isKnownPackage(AdapterUtil.internalNameToPkg(call.owner))
            ) {
                continue;
            }
            TargetPair method = this.context.dirtyMethods().resolveInherited(MethodQualifier.create(call));
            if (method != null && !this.visited.contains(method.methodNode())) {
                Set<Integer> locals = PassedLocals.findPassedSlots(flow, call, call.getOpcode() != Opcodes.INVOKESTATIC, call.desc, caller.originLocals());
                boolean mirrorable = fromRoot && call.getOpcode() == Opcodes.INVOKESTATIC && !method.classNode().name.equals(from.classNode().name);
                addCallee(callees, new Callee(caller.root(), method, locals, mirrorable ? call : null, fromRoot ? call : caller.rootCall()));
            }
        }

        for (IndyHandle lambda : MethodAnalyzer.findOwnIndyHandles(from.classNode(), from.methodNode())) {
            if (!this.visited.contains(lambda.method())) {
                Handle handle = lambda.handle();
                Set<Integer> locals = PassedLocals.findPassedSlots(flow, lambda.indy(), handle.getTag() != Opcodes.H_INVOKESTATIC, handle.getDesc(), caller.originLocals());
                addCallee(callees, new Callee(caller.root(), new TargetPair(from.classNode(), lambda.method()), locals, null, fromRoot ? null : caller.rootCall()));
            }
        }
        return callees;
    }

    private void addCallee(List<Callee> callees, Callee callee) {
        if (this.context.isStatic() || !callee.originLocals().isEmpty() || callee.calledFrom() != null) {
            this.visited.add(callee.method().methodNode());
            callees.add(callee);
        }
    }
}
