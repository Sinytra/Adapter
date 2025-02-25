package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.transformer.operation.DisableMixin;
import org.sinytra.adapter.patch.transformer.operation.ModifyMixinType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * Handle targeting redirectors to injections points that are being replaced with <code>instanceof</code> checks.
 * <p>
 * Sometimes, mods include injectors that widen item comparison if-statements from comparing specific item instances to an <code>instanceof</code> call
 * that applies to all items of a given type. While this is very nice of them, Forge already takes care of it for us, eliminating the needs for such a mixin.
 * <p/>
 * Reference: <code>stack.isOf(Items.CROSSBOW)</code> -> <code>stack.getItem() instanceof CrossbowItem</code> in <code>HeldItemRenderer#renderFirstPersonItem</code>
 */
public class DynFixSyntheticInstanceof implements DynamicFixer<DynFixSyntheticInstanceof.Data> {
    private static final Set<String> ACCEPTED_ANNOTATIONS = Set.of(MixinConstants.REDIRECT, MixinConstants.MODIFY_EXPR_VAL);
    private static final int RANGE = 4;

    public record Data(AbstractInsnNode cleanInjectionInsn) {}

    @Override
    @Nullable
    public Data prepare(MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesAny(ACCEPTED_ANNOTATIONS)
            && methodContext.hasInjectionPointValue("INVOKE")
            && methodContext.findCleanInjectionTarget() != null && methodContext.findDirtyInjectionTarget() != null
        ) {
            List<AbstractInsnNode> insns = methodContext.findInjectionTargetInsns(methodContext.findCleanInjectionTarget()); 
            return new Data(insns.getFirst());
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        AbstractInsnNode targetInsn = data.cleanInjectionInsn();
        List<AbstractInsnNode> labelInsns = findLabelInsns(targetInsn);
        AbstractInsnNode jumpInsn = labelInsns.getLast();
        // Ensure label contain an if statement
        if (!(jumpInsn instanceof JumpInsnNode)) {
            return null;
        }
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findForwardInstructions(targetInsn, RANGE);
        int firstOp = cleanMatcher.after().getFirst().getOpcode();
        // Find equivalent dirty code point
        InsnList dirtyInsns = methodContext.findDirtyInjectionTarget().methodNode().instructions;
        for (AbstractInsnNode insn : dirtyInsns) {
            if (insn.getOpcode() == firstOp) {
                AbstractInsnNode nextLabel = findInsnAfterLabel(insn);
                InstructionMatcher dirtyMatcher = MethodCallAnalyzer.findForwardInstructions(nextLabel, RANGE);
                if (cleanMatcher.test(dirtyMatcher)) {
                    // ModifyExpressionValue doesn't include the original instanceof call, so we can skip comparing instructions
                    if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
                        TypeInsnNode instanceOfInsn = (TypeInsnNode) findLabelInsns(insn).stream().filter(i -> i.getOpcode() == Opcodes.INSTANCEOF).findFirst().orElse(null);
                        if (instanceOfInsn == null) {
                            return null;
                        }
                        MethodTransform transform = new ModifyMixinType(MixinConstants.MODIFY_INSTANCEOF_VAL, b -> {
                            b.sameTarget().injectionPoint("sinytra:INSTANCEOF", instanceOfInsn.desc);
                            int ordinal = getInstanceofOrdinal(dirtyInsns, instanceOfInsn);
                            if (ordinal != 0) {
                                b.putValue("ordinal", ordinal);
                            }
                        });
                        return FixResult.of(transform.apply(methodContext), PatchAuditTrail.Match.FULL);
                    }

                    // Found the code point, now determine the contents of the updated if statement
                    List<AbstractInsnNode> dirtyLabelInsns = findLabelInsns(insn);

                    // Create a normalized method body insn list
                    List<AbstractInsnNode> modLabelInsns = new ArrayList<>();
                    for (AbstractInsnNode ins : methodNode.instructions) {
                        if (ins instanceof LineNumberNode || ins instanceof FrameNode) {
                            continue;
                        }
                        modLabelInsns.add(ins.clone(Map.of()));
                    }
                    // Remove first and last label insns
                    modLabelInsns.removeFirst();
                    modLabelInsns.removeLast();

                    // Remove consumer insns (jump / return)
                    dirtyLabelInsns.removeLast();
                    modLabelInsns.removeLast();

                    // Test whether the mixin method's body is functionally equal to the patched if statement
                    InstructionMatcher finalCleanMatcher = new InstructionMatcher(null, dirtyLabelInsns, List.of());
                    InstructionMatcher finalDirtyMatcher = new InstructionMatcher(null, modLabelInsns, List.of());

                    // Disable mixin. Goodbye.
                    if (finalCleanMatcher.test(finalDirtyMatcher, InsnComparator.IGNORE_VAR_INDEX)) {
                        return FixResult.of(new DisableMixin().apply(methodContext), PatchAuditTrail.Match.PARTIAL);
                    }
                }
            }
        }

        return null;
    }

    private static AbstractInsnNode findInsnAfterLabel(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        for (; next != null; next = next.getNext()) {
            if (next instanceof LabelNode) {
                break;
            }
        }
        return next;
    }

    private static List<AbstractInsnNode> findLabelInsns(AbstractInsnNode insn) {
        List<AbstractInsnNode> list = new ArrayList<>();
        if (!(insn instanceof LabelNode)) {
            for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                if (prev instanceof LabelNode) {
                    break;
                }
                if (prev instanceof FrameNode || prev instanceof LineNumberNode) {
                    continue;
                }

                list.add(prev);
            }
        }
        for (AbstractInsnNode next = insn.getNext(); next != null; next = next.getNext()) {
            if (next instanceof LabelNode) {
                break;
            }
            if (next instanceof FrameNode || next instanceof LineNumberNode) {
                continue;
            }
            list.add(next);
        }
        return list;
    }

    private static int getInstanceofOrdinal(InsnList insns, AbstractInsnNode insn) {
        List<AbstractInsnNode> instanceOfInsns = new ArrayList<>();
        for (AbstractInsnNode node : insns) {
            if (node.getOpcode() == Opcodes.INSTANCEOF) {
                instanceOfInsns.add(insn);
            }
        }
        return instanceOfInsns.indexOf(insn);
    }
}
