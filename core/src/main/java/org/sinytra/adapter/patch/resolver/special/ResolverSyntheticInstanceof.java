package org.sinytra.adapter.patch.resolver.special;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.config.Configurations;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.sinytra.adapter.analysis.InsnComparator;
import org.sinytra.adapter.analysis.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.env.ctx.TargetPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_SINYTRA_INSTANCEOF;

/**
 * <p>
 * Handle targeting redirectors to injections points that are being replaced with <code>instanceof</code> checks.
 * <p>
 * Sometimes, mods include injectors that widen item comparison if-statements from comparing specific item instances to an <code>instanceof</code> call
 * that applies to all items of a given type. While this is very nice of them, Forge already takes care of it for us, eliminating the needs for such a mixin.
 * <p/>
 * Reference: <code>stack.isOf(Items.CROSSBOW)</code> -> <code>stack.getItem() instanceof CrossbowItem</code> in <code>HeldItemRenderer#renderFirstPersonItem</code>
 */
public record ResolverSyntheticInstanceof(boolean skipInsnComparison) implements Resolver {

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE))
            return ResolutionResult.pass();

        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        AbstractInsnNode targetInsn = context.methods().findInjectionTargetInsn(cleanTarget);
        if (targetInsn == null) return ResolutionResult.pass();

        List<AbstractInsnNode> labelInsns = findLabelInsns(targetInsn);
        AbstractInsnNode jumpInsn = labelInsns.getLast();
        // Ensure label contain an if statement
        if (!(jumpInsn instanceof JumpInsnNode)) return ResolutionResult.pass();

        InstructionMatcher cleanMatcher = MethodInsnMatcher.findForwardInstructions(targetInsn);
        int firstOp = cleanMatcher.after().getFirst().getOpcode();
        // Find equivalent dirty code point
        InsnList dirtyInsns = dirtyTarget.methodNode().instructions;
        for (AbstractInsnNode insn : dirtyInsns) {
            if (insn.getOpcode() == firstOp) {
                AbstractInsnNode nextLabel = findInsnAfterLabel(insn);
                InstructionMatcher dirtyMatcher = MethodInsnMatcher.findForwardInstructions(nextLabel);
                if (cleanMatcher.test(dirtyMatcher)) {
                    // ModifyExpressionValue doesn't include the original instanceof call, so we can skip comparing instructions
                    if (this.skipInsnComparison) {
                        TypeInsnNode instanceOfInsn = (TypeInsnNode) findLabelInsns(insn).stream().filter(i -> i.getOpcode() == Opcodes.INSTANCEOF).findFirst().orElse(null);
                        if (instanceOfInsn == null) {
                            return ResolutionResult.pass();
                        }

                        int ordinal = getInstanceofOrdinal(dirtyInsns, instanceOfInsn);
                        Configuration config = recipe.dirty().copyClean()
                            .setMixinType(MixinAnnotations.MODIFY_INSTANCEOF_VAL)
                            .inheritTargetClass()
                            .inheritTargetMethod()
                            .setAtData(AtData.builder(AT_VAL_SINYTRA_INSTANCEOF)
                                .target(instanceOfInsn.desc)
                                .ordinal(ordinal != 0 ? ordinal : null)
                                .build())
                            .inheritParameters()
                            .inheritReturnType();

                        return ResolutionResult.replace(config);
                    }

                    // Found the code point, now determine the contents of the updated if statement
                    List<AbstractInsnNode> dirtyLabelInsns = findLabelInsns(insn);

                    // Create a normalized method body insn list
                    List<AbstractInsnNode> modLabelInsns = new ArrayList<>();
                    for (AbstractInsnNode ins : context.methodNode().instructions) {
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
                        return ResolutionResult.replace(Configurations.DELETE);
                    }
                }
            }
        }

        return ResolutionResult.pass();
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
