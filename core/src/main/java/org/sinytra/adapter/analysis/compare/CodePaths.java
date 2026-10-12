package org.sinytra.adapter.analysis.compare;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.flow.DataFlow.ImplicitCalls;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.*;
import java.util.function.Predicate;

public class CodePaths {
    private static final Set<String> IGNORED_PACKAGES = Set.of("java/", "javax/", "jdk/", "sun/", "com/sun/");
    private static final int MAX_DEPTH = 5;

    @Nullable
    public static CodePath findCodePath(TargetPair from, TargetPair to, Set<Integer> trackLocals, MixinContext context, Predicate<MethodInsnNode> entryFilter) {
        if (from == null || to == null) {
            return null;
        }

        CodePath path = findCodePathRecursive(from, to, trackLocals, context, entryFilter, new HashMap<>(), 0);
        if (path == null) {
            return null;
        }

        List<CodePathStep> steps = path.steps();

        Map<Integer, Integer> finalLocals = new Int2IntOpenHashMap();
        for (Integer original : trackLocals) {
            Integer current = original;

            for (int i = 0; i < steps.size() - 1; i++) {
                Integer next = steps.get(i).locals().get(current);
                if (next == null) {
                    current = null;
                    break;
                }
                current = next;
            }

            if (current != null) {
                finalLocals.put(original, current);
            }
        }

        return new CodePath(path.from(), path.to(), steps, finalLocals);
    }

    @Nullable
    private static CodePath findCodePathRecursive(TargetPair from, TargetPair to, Set<Integer> trackLocals, MixinContext context,
                                                  Predicate<MethodInsnNode> filter, Map<String, Integer> visited, int depth
    ) {
        if (depth >= MAX_DEPTH) {
            return null;
        }
        String key = from.classNode().name + "." + from.methodNode().name + from.methodNode().desc + trackLocals;
        Integer visitedDepth = visited.get(key);
        if (visitedDepth != null && visitedDepth <= depth) {
            return null;
        }
        visited.put(key, depth);

        List<MethodInsnNode> topTierCalls = context.dataFlow(from).findTopLevelCalls(ImplicitCalls.UNWRAP);

        for (MethodInsnNode minsn : topTierCalls) {
            if (!filter.test(minsn)
                || IGNORED_PACKAGES.stream().anyMatch(minsn.owner::startsWith)
                || !context.patchContext().environment().isKnownPackage(AdapterUtil.internalNameToPkg(minsn.owner))
            ) {
                continue;
            }

            TargetPair nextFrom = context.dirtyMethods().resolveInherited(MethodQualifier.create(minsn));
            if (nextFrom == null) continue;

            LocalVariableLookup lvt = LocalVariableLookup.of(nextFrom.methodNode());
            if (lvt == null) {
                continue;
            }

            Map<Integer, Integer> locals = new Int2IntOpenHashMap();
            if (!trackLocals.isEmpty()) {
                List<List<AbstractInsnNode>> receiverInsns = context.dataFlow(from).findOperandExpressions(minsn);
                for (int i = 0; i < receiverInsns.size(); i++) {
                    List<AbstractInsnNode> argInsns = receiverInsns.get(i);
                    if (argInsns.size() != 1 || !(argInsns.getFirst() instanceof VarInsnNode varInsn)) continue;

                    LocalVariableNode local = lvt.getByOrdinalOrNull(i);
                    if (local == null) continue;

                    for (Integer index : trackLocals) {
                        if (varInsn.var == index) {
                            locals.put(index, local.index);
                        }
                    }
                }
            }

            if (!locals.keySet().containsAll(trackLocals)) {
                continue;
            }

            CodePathStep step = new CodePathStep(from, nextFrom, locals);

            if (nextFrom.classNode().name.equals(to.classNode().name)
                && nextFrom.methodNode().name.equals(to.methodNode().name)
                && nextFrom.methodNode().desc.equals(to.methodNode().desc)
            ) {
                return new CodePath(from, to, List.of(step), Map.of());
            }

            Set<Integer> nextTrackLocals = Set.copyOf(locals.values());
            CodePath subPath = findCodePathRecursive(nextFrom, to, nextTrackLocals, context, call -> true, visited, depth + 1);
            if (subPath != null) {
                List<CodePathStep> steps = new ArrayList<>();
                steps.add(step);
                steps.addAll(subPath.steps());
                return new CodePath(from, to, steps, Map.of());
            }
        }

        return null;
    }

    public record CodePath(TargetPair from, TargetPair to, List<CodePathStep> steps, Map<Integer, Integer> locals) {
    }

    public record CodePathStep(TargetPair from, TargetPair to, Map<Integer, Integer> locals) {
    }
}
