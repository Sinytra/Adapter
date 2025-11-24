package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

public class ArbitraryInjectionPointSubResolver implements SubResolver {
    // TODO Remove type hardcoding
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_ARG, MixinConstants.MODIFY_EXPR_VAL);
    private static final int INSN_RANGE = 5;

    private record Data(MethodContext.TargetPair dirtyTarget, AbstractInsnNode cleanInjectionInsn) {
    }

    @Nullable
    @Override
    public Configuration resolve(MixinData mixin, MixinContext context, Configuration clean, Configuration dirty, Recipe recipe) {
        String injectionPointTarget = clean.getAtData().getTarget().orElse(null);
        if (injectionPointTarget == null) return null;

        Data data = prepare(context, clean, dirty);
        if (data == null) return null;

        MethodInsnNode targetMethodCall = resolveTargetCallInsn(data, context, injectionPointTarget);
        if (targetMethodCall == null) return null;

        if (targetMethodCall.owner.equals(data.dirtyTarget().classNode().name)) {
            MethodContext.TargetPair target = context.methods().findOwnMethodPair(context.dirtyLookup(), MethodQualifier.create(targetMethodCall));
            if (target == null) return null;

            List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(target);
            if (insns.isEmpty()) {
                Type returnType = Type.getReturnType(context.methodNode().desc);
                Type dirtyReturnType = Type.getReturnType(target.methodNode().desc);
                // For MEV it's enough that the return types match
                if (context.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL) && returnType.equals(dirtyReturnType)) {
                    return MutableConfiguration.create()
                        .setAtData(new AtData(AT_VAL_INVOKE, MethodQualifier.create(targetMethodCall).asDescriptor(), null));
                }
                return null;
            }
        }

        // New method is in the same class? It's possible our target injection point was moved there
        if (targetMethodCall.owner.equals(data.dirtyTarget().classNode().name) && !context.methodAnnotation().matchesDesc(MixinConstants.INJECT)) {
            return MutableConfiguration.create()
                .setTargetMethod(MethodQualifier.create(targetMethodCall));
        }

        return MutableConfiguration.create()
            .setAtData(new AtData(AT_VAL_INVOKE, MethodQualifier.create(targetMethodCall).asDescriptor(), null));
    }

    private MethodInsnNode resolveTargetCallInsn(Data data, MixinContext context, String injectionPointTarget) {
        MethodNode dirtyTargetMethod = data.dirtyTarget().methodNode();
        AbstractInsnNode cleanInjectionInsn = data.cleanInjectionInsn();
        AbstractInsnNode nextCallCandidate = findCandidates(MethodCallAnalyzer.findBackwardsInstructions(cleanInjectionInsn, INSN_RANGE).inverse(), dirtyTargetMethod, List::getLast);

        if (nextCallCandidate != null) {
            return findReplacementInjectionPoint(nextCallCandidate, AbstractInsnNode::getNext, context, injectionPointTarget);
        } else {
            AbstractInsnNode previousCallCandidate = findCandidates(MethodCallAnalyzer.findForwardInstructions(cleanInjectionInsn, INSN_RANGE), dirtyTargetMethod, List::getFirst);
            if (previousCallCandidate != null) {
                return findReplacementInjectionPoint(previousCallCandidate, AbstractInsnNode::getPrevious, context, injectionPointTarget);
            }
        }

        return null;
    }

    private Data prepare(MixinContext context, Configuration clean, Configuration dirty) {
        if (context.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS)) {
            MethodQualifier dirtyQualifier = dirty.getTargetMethod();
            if (dirtyQualifier == null) return null;
            MethodContext.TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), dirtyQualifier);
            if (dirtyTarget == null) return null;

            MethodContext.TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), clean.getTargetMethod());
            List<AbstractInsnNode> cleanInsns = context.methods().findInjectionTargetInsns(cleanTarget);
            if (cleanInsns.size() == 1 && dirtyTarget != null) {
                AbstractInsnNode cleanInjectionInsn = cleanInsns.getFirst();
                return new Data(dirtyTarget, cleanInjectionInsn);
            }
        }
        return null;
    }

    private static AbstractInsnNode findCandidates(InstructionMatcher cleanMatcher, MethodNode dirtyTargetMethod, Function<List<AbstractInsnNode>, AbstractInsnNode> selector) {
        // Find an common instruction in the clean and dirty target methods
        if (cleanMatcher.after().isEmpty()) return null;
        int firstOpcode = cleanMatcher.after().getFirst().getOpcode();
        List<AbstractInsnNode> candidates = new ArrayList<>();

        for (AbstractInsnNode insn : dirtyTargetMethod.instructions) {
            if (insn instanceof FrameNode || insn instanceof LineNumberNode || insn.getOpcode() != firstOpcode) {
                continue;
            }

            InstructionMatcher dirtyMatcher = MethodCallAnalyzer.findForwardInstructionsDirect(insn, INSN_RANGE);
            if (cleanMatcher.test(dirtyMatcher, InsnComparator.IGNORE_VAR_INDEX)) {
                // Find first method call past matched instruction
                AbstractInsnNode lastInsn = selector.apply(dirtyMatcher.after());
                if (lastInsn != null) {
                    candidates.add(lastInsn);
                }
            }
        }

        return !candidates.isEmpty() ? candidates.getFirst() : null;
    }

    private static MethodInsnNode findReplacementInjectionPoint(AbstractInsnNode lastInsn, UnaryOperator<AbstractInsnNode> flow, MixinContext context, String injectionPointTarget) {
        // Require matching return types for ModifyExpressionValue mixins
        if (context.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
            Type desiredReturnType = Type.getReturnType(injectionPointTarget);
            return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow,
                v -> v instanceof MethodInsnNode minsn && Type.getReturnType(minsn.desc).equals(desiredReturnType));
        }
        return (MethodInsnNode) AdapterUtil.iterateInsns(lastInsn, flow, v -> v instanceof MethodInsnNode);
    }
}
