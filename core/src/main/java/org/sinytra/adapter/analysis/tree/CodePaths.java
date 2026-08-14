package org.sinytra.adapter.analysis.tree;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CodePaths {
    @Nullable
    public static CodePath findCodePath(TargetPair from, TargetPair to, Set<Integer> trackLocals, MixinContext context) {
        if (from == null || to == null) {
            return null;
        }

        CodePath path = findCodePathRecursive(from, to, trackLocals, context, 0, 5);
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
    private static CodePath findCodePathRecursive(TargetPair from, TargetPair to, Set<Integer> trackLocals, MixinContext context, int depth, int limit) {
        if (depth >= limit) {
            return null;
        }

        List<MethodInsnNode> topTierCalls = MethodAnalyzer.getTopTierMethodCalls(from, true);

        for (MethodInsnNode minsn : topTierCalls) {
            if (!context.patchContext().environment().isKnownPackage(AdapterUtil.internalNameToPkg(minsn.owner))) {
                continue;
            }

            TargetPair nextFrom = context.methods().findInheritedMethodPair(context.dirtyLookup(), MethodQualifier.create(minsn));
            if (nextFrom == null) continue;

            LocalVariableLookup lvt = context.methods().getLVT(nextFrom.methodNode());
            if (lvt == null) {
                continue;
            }

            Map<Integer, Integer> locals = new Int2IntOpenHashMap();
            List<List<AbstractInsnNode>> receiverInsns = MethodCallAnalyzer.getMethodCallArgInsns(from.methodNode(), minsn);

            if (!trackLocals.isEmpty()) {
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
                && nextFrom.methodNode().name.equals(to.methodNode().name)) {
                return new CodePath(from, to, List.of(step), Map.of());
            }

            Set<Integer> nextTrackLocals = Set.copyOf(locals.values());
            CodePath subPath = findCodePathRecursive(nextFrom, to, nextTrackLocals, context, depth + 1, limit);
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
