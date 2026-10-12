package org.sinytra.adapter.pipeline.resolver.special;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.mixin.MixinBridge;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.Resolver;

import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_RETURN;

/**
 * Original mixin:
 *
 * <pre>{@code @ModifyVariable(
 *     method = "exampleMethod",
 *     at = @At("RETURN")
 * )
 * private void someMethodMixin(int original) {
 *     return original * 2;
 * }
 * }</pre>
 * <p>
 * Original target:
 *
 * <pre>{@code
 * public int exampleMethod() {
 *     int i = 10;
 *     // ...
 * <<< return i;
 * >>> return localvar$zfk000$someMethodMixin(i);
 * }
 * }</pre>
 * <p>
 * Patched target:
 * <pre>{@code
 * public int exampleMethod() {
 *     int i = 10;
 *     // ...
 * <<< return EventHooks.wrapVariable(i);
 * >>> return EventHooks.wrapVariable(modify$zfk000$someMethodMixin(i));
 * }
 * }</pre>
 * <p>
 * Patched mixin:
 * 
 * <pre>{@code @ModifyArg(
 *     method = "exampleMethod",
 *     at = @At(
 *         value = "INVOKE",
 *         target="Lcom/example/EventHooks;wrapVariable(I)I"
 *     ),
 *     index = 0
 * )
 * private void someMethodMixin(int original) {
 *     return original * 2;
 * }
 * }</pre>
 */
public class ModifyVarAtReturnResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        AtData at = recipe.clean().getAtData();
        if (!AT_VAL_RETURN.equals(at.getValue())) return ResolutionResult.pass();

        // Find injection targets
        Targets targets = recipe.targets();
        if (targets == null) return ResolutionResult.pass();

        int ordinal = at.getOrdinal().orElse(-1);
        Pair<AbstractInsnNode, Integer> cleanTargetPair = getTargetPair(recipe.clean(), context, targets.clean(), ordinal);
        // In CLEAN, previous insn is VarInsn for the modified variable
        if (cleanTargetPair == null || !(cleanTargetPair.getFirst() instanceof VarInsnNode cleanVarInsn) || cleanVarInsn.var != cleanTargetPair.getSecond()) {
            return ResolutionResult.pass();
        }
        // In DIRTY, previous insn (as in, before the RETURN insn) is a method call
        Pair<AbstractInsnNode, Integer> dirtyTargetPair = getTargetPair(recipe.clean(), context, targets.dirty(), ordinal);
        // In CLEAN, previous insn is VarInsn for the modified variable
        if (dirtyTargetPair == null || !(dirtyTargetPair.getFirst() instanceof MethodInsnNode dirtyMinsn)) {
            return ResolutionResult.pass();
        }
        // Get method call argument instructions
        List<AbstractInsnNode> args = context.dataFlow(targets.dirty()).resolveCallOperandProducers(dirtyMinsn);
        if (args == null) {
            return ResolutionResult.pass();
        }

        for (int i = 0; i < args.size(); i++) {
            AbstractInsnNode insn = args.get(i);
            if (insn instanceof VarInsnNode varInsn && varInsn.var == cleanTargetPair.getSecond()) {
                Configuration config = recipe.dirty().copyClean()
                    .setMixinType(MixinAnnotations.MODIFY_ARG)
                    .inheritTargetClass()
                    .inheritTargetMethod()
                    .setAtData(AtData.create(AT_VAL_INVOKE, dirtyMinsn))
                    .inheritSignature()
                    .setProperty(MixinKeys.INDEX, i);

                String qualifier = MethodQualifier.create(dirtyMinsn).asDescriptor();
                context.recordCtxAudit("Redirect RETURN variable modifier to parameter %s of method call to %s", i, qualifier);
                return ResolutionResult.replace(config);
            }
        }

        return ResolutionResult.pass();
    }

    private static Pair<AbstractInsnNode, Integer> getTargetPair(Configuration clean, MixinContext context, TargetPair injectionTarget, int ordinal) {
        // Find injection point insn
        List<AbstractInsnNode> targetInsns = context.injections().find(injectionTarget);
        if (targetInsns.isEmpty()) return null;

        int index = ordinal == -1 ? targetInsns.size() - 1 : ordinal;
        if (index >= targetInsns.size()) return null;

        AbstractInsnNode targetInsn = targetInsns.get(index);
        // Find modified variable
        MixinBridge.Session session = MixinBridge.session(
            context.classNode().name,
            context.methodNode(), injectionTarget.classNode(), injectionTarget.methodNode(),
            context.environment().refmapHolder()
        );
        int local = session.findLocal(context.methodAnnotation().unwrap(), clean.getReturnType(), targetInsn);
        return Pair.of(targetInsn, local);
    }
}
