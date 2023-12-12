package dev.su5ed.sinytra.adapter.gradle.analysis;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.gradle.util.MatchResult;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
import dev.su5ed.sinytra.adapter.patch.transformer.filter.InjectionPointTransformerFilter;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;

import static dev.su5ed.sinytra.adapter.gradle.ClassAnalyzer.LAMBDA_PREFIX;
import static dev.su5ed.sinytra.adapter.gradle.ClassAnalyzer.containsMethodCall;

public class OverloadedMethods {
    private static final Collection<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.DRETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN);

    @Nullable
    public static MethodOverload findOverloadMethod(AnalysisContext context, String owner, MethodNode method, Collection<MethodNode> others) {
        List<Pair<MethodNode, List<String>>> found = new ArrayList<>();
        List<MethodNode> lowPriority = new ArrayList<>();
        for (MethodNode other : others) {
            MatchResult matchResult = checkParameters(other, method);
            if (matchResult != MatchResult.FULL) {
                if (matchResult == MatchResult.PARTIAL) {
                    lowPriority.add(other);
                }
                continue;
            }
            isOverloadedMethod(context, other, owner, method)
                .ifPresent(exclusions -> found.add(Pair.of(other, exclusions)));
        }
        if (!found.isEmpty()) {
            if (found.size() == 1) {
                return new MethodOverload(true, found.get(0).getFirst(), found.get(0).getSecond());
            }
            return null;
        }
        for (MethodNode other : lowPriority) {
            isOverloadedMethod(context, other, owner, method)
                .ifPresent(exclusions -> found.add(Pair.of(other, exclusions)));
        }
        return found.size() == 1 ? new MethodOverload(false, found.get(0).getFirst(), found.get(0).getSecond()) : null;
    }

    private static Optional<List<String>> isOverloadedMethod(AnalysisContext context, MethodNode other, String owner, MethodNode method) {
        if (context.remapMethod(owner, other.name, other.desc).startsWith(LAMBDA_PREFIX) || method.name.startsWith(LAMBDA_PREFIX)) {
            return Optional.empty();
        }
        if (method.name.equals("<init>") && !other.name.equals("<init>")) {
            return Optional.empty();
        }
        MethodCallInterpreter interpreter = AdapterUtil.analyzeMethod(other, new MethodCallInterpreter());

        List<Pair<AbstractInsnNode, MethodInsnNode>> insns = interpreter.getInsns();
        if (insns.isEmpty()) {
            return Optional.empty();
        }
        Pair<AbstractInsnNode, MethodInsnNode> last = insns.get(insns.size() - 1);
        if (insns.size() > 1 && other.instructions.indexOf(insns.get(0).getFirst()) < other.instructions.indexOf(last.getFirst())) {
            return Optional.empty();
        }
        AbstractInsnNode start = last.getFirst();
        boolean seenLabel = false;
        for (AbstractInsnNode previous = start.getPrevious(); previous != null; previous = previous.getPrevious()) {
            if (previous instanceof LabelNode) {
                if (!seenLabel) {
                    seenLabel = true;
                } else {
                    return Optional.empty();
                }
            }
        }
        MethodInsnNode minsn = last.getSecond();
        if (minsn.owner.equals(owner) && minsn.name.equals(method.name) && minsn.desc.equals(method.desc)) {
            boolean returnSeen = false;
            for (AbstractInsnNode next = minsn.getNext(); next != null; next = next.getNext()) {
                // Skip debug nodes
                if (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode) {
                    continue;
                }
                // Find first (and single) return after the dirtyMethod call
                if (RETURN_OPCODES.contains(next.getOpcode())) {
                    // Multiple returns found
                    if (returnSeen) {
                        returnSeen = false;
                        break;
                    }
                    returnSeen = true;
                } else {
                    // Invalid insn found
                    returnSeen = false;
                    break;
                }
            }
            if (returnSeen) {
                List<String> exludedInjectionPoints = new ArrayList<>();
                if (insns.size() > 1) {
                    for (int i = 0; i < insns.size() - 1; i++) {
                        MethodInsnNode callInsn = insns.get(i).getSecond();
                        if (containsMethodCall(context.getCleanMethod(other), callInsn) && !containsMethodCall(method, callInsn)) {
                            exludedInjectionPoints.add(MethodCallAnalyzer.getCallQualifier(callInsn));
                        }
                    }
                }
                return Optional.of(exludedInjectionPoints);
            }
        }
        return Optional.empty();
    }

    public static MatchResult checkParameters(MethodNode clean, MethodNode dirty) {
        return checkParameters(Type.getArgumentTypes(clean.desc), Type.getArgumentTypes(dirty.desc));
    }

    // Check if dirtyMethod begins with cleanMethod's params
    public static MatchResult checkParameters(Type[] parameterTypes, Type[] dirtyParameterTypes) {
        if (parameterTypes.length > dirtyParameterTypes.length) {
            return MatchResult.NONE;
        }
        MatchResult result = MatchResult.FULL;
        int i = 0;
        for (int j = 0; i < parameterTypes.length && j < dirtyParameterTypes.length; j++) {
            Type type = dirtyParameterTypes[j];
            if (!parameterTypes[i].equals(type)) {
                result = MatchResult.PARTIAL;
            }
            i++;
        }
        return result;
    }

    public record MethodOverload(boolean isFullMatch, MethodNode methodNode, List<String> excludedInjectionPoints) {
        public void applyPatchTargetModifier(Patch.ClassPatchBuilder builder, MethodNode method) {
            if (this.excludedInjectionPoints.isEmpty()) {
                builder.modifyTarget(method.name + method.desc);
            } else {
                builder.transform(InjectionPointTransformerFilter.create(new ModifyInjectionTarget(List.of(method.name + method.desc)), this.excludedInjectionPoints));
            }
        }
    }

    private static class MethodCallInterpreter extends SourceInterpreter {
        private final Set<AbstractInsnNode> seen = new HashSet<>();
        private final List<Pair<AbstractInsnNode, MethodInsnNode>> insns = new ArrayList<>();

        public MethodCallInterpreter() {
            super(Opcodes.ASM9);
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode minsn && !this.seen.contains(minsn)) {
                if (values.isEmpty()) {
                    this.insns.add(Pair.of(minsn, minsn));
                } else {
                    SourceValue value = values.get(0);
                    if (value.insns.size() == 1) {
                        AbstractInsnNode valueInsn = value.insns.iterator().next();
                        this.insns.add(Pair.of(valueInsn, minsn));
                    }
                }
                this.seen.add(minsn);
            }
            return super.naryOperation(insn, values);
        }

        public List<Pair<AbstractInsnNode, MethodInsnNode>> getInsns() {
            return this.insns;
        }
    }
}
