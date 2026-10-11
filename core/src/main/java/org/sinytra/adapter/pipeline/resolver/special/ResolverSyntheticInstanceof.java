package org.sinytra.adapter.pipeline.resolver.special;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InsnComparator;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.Configurations;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_SINYTRA_INSTANCEOF;

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

        TargetPair dirtyTarget = recipe.getDirtyTarget();
        AbstractInsnNode targetInsn = recipe.getCleanInjectionInsn();
        if (dirtyTarget == null || targetInsn == null) return ResolutionResult.pass();

        List<AbstractInsnNode> labelInsns = findLabelInsns(targetInsn);
        // Ensure label contain an if statement
        if (labelInsns.isEmpty() || !(labelInsns.getLast() instanceof JumpInsnNode)) return ResolutionResult.pass();

        InstructionMatcher cleanMatcher = MethodInsnMatcher.findForwardInstructions(targetInsn);
        if (cleanMatcher.after().isEmpty()) return ResolutionResult.pass();
        int firstOp = cleanMatcher.after().getFirst().getOpcode();
        // Find equivalent dirty code point
        InsnList dirtyInsns = dirtyTarget.methodNode().instructions;
        for (AbstractInsnNode insn : dirtyInsns) {
            if (insn.getOpcode() == firstOp) {
                AbstractInsnNode nextLabel = InsnTraversal.find(insn, InsnTraversal.FORWARD, LabelNode.class::isInstance);
                InstructionMatcher dirtyMatcher = MethodInsnMatcher.findForwardInstructions(nextLabel);
                if (cleanMatcher.test(dirtyMatcher)) {
                    // ModifyExpressionValue doesn't include the original instanceof call, so we can skip comparing instructions
                    if (this.skipInsnComparison) {
                        // Only convert to @ModifyInstanceofValue if the handler returns boolean.
                        // If it returns a non-boolean type (e.g. Block), the mixin is modifying the
                        // value fed into the instanceof check, not the boolean result of it.
                        // Converting in that case produces an invalid (Block)Block handler where (Z)Z is expected.
                        Type handlerReturnType = Type.getReturnType(context.methodNode().desc);
                        if (!handlerReturnType.equals(Type.BOOLEAN_TYPE)) {
                            return ResolutionResult.pass();
                        }

                        TypeInsnNode instanceOfInsn = (TypeInsnNode) findLabelInsns(insn).stream().filter(i -> i.getOpcode() == Opcodes.INSTANCEOF).findFirst().orElse(null);
                        if (instanceOfInsn == null) {
                            return ResolutionResult.pass();
                        }

                        int ordinal = getInstanceofOrdinal(dirtyInsns, instanceOfInsn);
                        Configuration config = Configurations.changeMixinType(recipe.dirty(), MixinAnnotations.MODIFY_INSTANCEOF_VAL,
                            AtData.builder(AT_VAL_SINYTRA_INSTANCEOF)
                                .target(instanceOfInsn.desc)
                                .ordinal(ordinal != 0 ? ordinal : null)
                                .build());

                        return ResolutionResult.replace(config);
                    }

                    // Found the code point, now determine the contents of the updated if statement
                    List<AbstractInsnNode> dirtyLabelInsns = findLabelInsns(insn);

                    // Create a normalized method body insn list
                    List<AbstractInsnNode> modLabelInsns = new ArrayList<>();
                    for (AbstractInsnNode ins : context.methodNode().instructions) {
                        if (InsnTraversal.isDebugNode(ins)) {
                            continue;
                        }
                        modLabelInsns.add(ins.clone(Map.of()));
                    }
                    if (modLabelInsns.size() < 3 || dirtyLabelInsns.isEmpty()) {
                        continue;
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

    // The instructions around insn within its label
    private static List<AbstractInsnNode> findLabelInsns(AbstractInsnNode insn) {
        List<AbstractInsnNode> list = new ArrayList<>();
        if (!(insn instanceof LabelNode)) {
            list.addAll(InsnTraversal.untilLabel(insn, InsnTraversal.BACKWARD));
        }
        list.addAll(InsnTraversal.untilLabel(insn, InsnTraversal.FORWARD));
        return list;
    }

    private static int getInstanceofOrdinal(InsnList insns, TypeInsnNode insn) {
        List<AbstractInsnNode> instanceOfInsns = new ArrayList<>();
        for (AbstractInsnNode node : insns) {
            if (node instanceof TypeInsnNode typeInsn && typeInsn.getOpcode() == Opcodes.INSTANCEOF && typeInsn.desc.equals(insn.desc)) {
                instanceOfInsns.add(node);
            }
        }
        return instanceOfInsns.indexOf(insn);
    }
}
