package org.sinytra.adapter.patch.transformer.dynamic;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.transformer.DisableMixin;
import org.sinytra.adapter.patch.transformer.ModifyMixinType;

import java.util.*;

/**
 * <p>
 * Handle targeting redirectors to injections points that are being replaced with <code>instanceof</code> checks.
 * <p>
 * Sometimes, mods include injectors that widen item comparison if-statements from comparing specific item instances to an <code>instanceof</code> call
 * that applies to all items of a given type. While this is very nice of them, Forge already takes care of it for us, eliminating the needs for such a mixin.
 * <p/>
 * Reference: <code>stack.isOf(Items.CROSSBOW)</code> -> <code>stack.getItem() instanceof CrossbowItem</code> in <code>HeldItemRenderer#renderFirstPersonItem</code>
 */
public class DynamicSyntheticInstanceofPatch implements MethodTransform {
    private static final int RANGE = 4;
    
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.REDIRECT, MixinConstants.MODIFY_EXPR_VAL);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        if (methodContext.injectionPointAnnotation().<String>getValue("value").map(v -> !v.get().equals("INVOKE")).orElse(true)) {
            return Patch.Result.PASS;
        }
        if (!methodContext.failsDirtyInjectionCheck()) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> insns = methodContext.findInjectionTargetInsns(methodContext.findCleanInjectionTarget());
        if (insns.size() != 1) {
            return Patch.Result.PASS;
        }
        AbstractInsnNode targetInsn = insns.get(0);
        List<AbstractInsnNode> labelInsns = findLabelInsns(targetInsn);
        AbstractInsnNode jumpInsn = labelInsns.get(labelInsns.size() - 1);
        // Ensure label contain an if statement
        if (!(jumpInsn instanceof JumpInsnNode)) {
            return Patch.Result.PASS;
        }
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findForwardInstructions(targetInsn, RANGE, true);
        int firstOp = cleanMatcher.after().get(0).getOpcode();
        // Find equivalent dirty code point
        InsnList dirtyInsns = methodContext.findDirtyInjectionTarget().methodNode().instructions;
        for (AbstractInsnNode insn : dirtyInsns) {
            if (insn.getOpcode() == firstOp) {
                AbstractInsnNode nextLabel = findInsnAfterLabel(insn);
                InstructionMatcher dirtyMatcher = MethodCallAnalyzer.findForwardInstructions(nextLabel, RANGE, true);
                if (cleanMatcher.test(dirtyMatcher)) {
                    // ModifyExpressionValue doesn't include the original instanceof call, so we can skip comparing instructions
                    if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_EXPR_VAL)) {
                        TypeInsnNode instanceOfInsn = (TypeInsnNode) findLabelInsns(insn).stream().filter(i -> i.getOpcode() == Opcodes.INSTANCEOF).findFirst().orElseThrow();
                        MethodTransform transform = new ModifyMixinType(MixinConstants.MODIFY_INSTANCEOF_VAL, b -> {
                            b.sameTarget().injectionPoint("sinytra:INSTANCEOF", instanceOfInsn.desc);
                            int ordinal = getInstanceofOrdinal(dirtyInsns, instanceOfInsn);
                            if (ordinal != 0) {
                                b.putValue("ordinal", ordinal);
                            }
                        });
                        return transform.apply(classNode, methodNode, methodContext, context);
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
                    modLabelInsns.remove(modLabelInsns.get(0));
                    modLabelInsns.remove(modLabelInsns.size() - 1);

                    // Remove consumer insns (jump / return)
                    dirtyLabelInsns.remove(dirtyLabelInsns.size() - 1);
                    modLabelInsns.remove(modLabelInsns.size() - 1);

                    // Test whether the mixin method's body is functionally equal to the patched if statement
                    InstructionMatcher finalCleanMatcher = new InstructionMatcher(null, dirtyLabelInsns, List.of());
                    InstructionMatcher finalDirtyMatcher = new InstructionMatcher(null, modLabelInsns, List.of());

                    // Disable mixin. Goodbye.
                    if (finalCleanMatcher.test(finalDirtyMatcher, InsnComparator.IGNORE_VAR_INDEX)) {
                        return new DisableMixin().apply(classNode, methodNode, methodContext, context);
                    }
                }
            }
        }

        return Patch.Result.PASS;
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
