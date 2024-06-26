package org.sinytra.adapter.patch.transformer.dynamic;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.ModifyMixinType;
import org.sinytra.adapter.patch.util.MockMixinRuntime;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
public class DynamicModifyVarAtReturnPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.MODIFY_VAR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle injectionPointAnnotation = methodContext.injectionPointAnnotation();
        if (injectionPointAnnotation == null) {
            return Patch.Result.PASS;
        }
        AnnotationValueHandle<String> targetHandle = injectionPointAnnotation.<String>getValue("value").orElse(null);
        if (targetHandle == null || !targetHandle.get().equals("RETURN")) {
            return Patch.Result.PASS;
        }
        int ordinal = injectionPointAnnotation.<Integer>getValue("ordinal").map(AnnotationValueHandle::get).orElse(-1);
        // Find injection targets
        MethodContext.TargetPair cleanTarget = methodContext.findCleanInjectionTarget();
        if (cleanTarget == null) {
            return Patch.Result.PASS;
        }
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        if (dirtyTarget == null) {
            return Patch.Result.PASS;
        }
        Pair<AbstractInsnNode, Integer> cleanTargetPair = getTargetPair(classNode, methodNode, methodContext, context, cleanTarget, ordinal);
        // In CLEAN, previous insn is VarInsn for the modified variable
        if (cleanTargetPair == null || !(cleanTargetPair.getFirst() instanceof VarInsnNode cleanVarInsn) || cleanVarInsn.var != cleanTargetPair.getSecond()) {
            return Patch.Result.PASS;
        }
        // In DIRTY, previous insn (as in, before the RETURN insn) is a method call
        Pair<AbstractInsnNode, Integer> dirtyTargetPair = getTargetPair(classNode, methodNode, methodContext, context, dirtyTarget, ordinal);
        // In CLEAN, previous insn is VarInsn for the modified variable
        if (dirtyTargetPair == null || !(dirtyTargetPair.getFirst() instanceof MethodInsnNode dirtyMinsn)) {
            return Patch.Result.PASS;
        }
        // Get method call argument instructions
        MethodCallInterpreter interpreter = MethodCallAnalyzer.analyzeInterpretMethod(dirtyTarget.methodNode(), new MethodCallInterpreter(dirtyMinsn));
        List<AbstractInsnNode> args = interpreter.getTargetArgs();
        if (args == null) {
            return Patch.Result.PASS;
        }

        Patch.Result result = Patch.Result.PASS;
        for (int i = 0; i < args.size(); i++) {
            AbstractInsnNode insn = args.get(i);
            if (insn instanceof VarInsnNode varInsn && varInsn.var == cleanTargetPair.getSecond()) {
                if (result != Patch.Result.PASS) {
                    // Cannot apply twice
                    return Patch.Result.PASS;
                }
                String qualifier = MethodCallAnalyzer.getCallQualifier(dirtyMinsn);
                final int index = i;
                LOGGER.info(PatchInstance.MIXINPATCH, "Redirecting RETURN variable modifier to parameter {} of method call to {}", i, qualifier);
                MethodTransform transform = new ModifyMixinType(MixinConstants.MODIFY_ARG, b -> b.sameTarget()
                    .injectionPoint("INVOKE", qualifier)
                    .putValue("index", index));
                result = transform.apply(classNode, methodNode, methodContext, context);
            }
        }

        return result;
    }

    private static Pair<AbstractInsnNode, Integer> getTargetPair(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, MethodContext.TargetPair injectionTarget, int ordinal) {
        // Find injection point insn
        List<AbstractInsnNode> targetInsns = methodContext.findInjectionTargetInsns(injectionTarget);
        if (targetInsns.isEmpty()) {
            return null;
        }
        int index = ordinal == -1 ? targetInsns.size() - 1 : ordinal;
        if (index >= targetInsns.size()) {
            return null;
        }
        AbstractInsnNode targetInsn = targetInsns.get(index);
        // Find modified variable
        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(methodContext.methodAnnotation().unwrap());
        InjectionInfo injectionInfo = MockMixinRuntime.forInjectionInfo(classNode.name, injectionTarget.classNode().name, context.environment());
        Type returnType = Type.getReturnType(methodNode.desc);
        Target target = new Target(injectionTarget.classNode(), injectionTarget.methodNode());
        LocalVariableDiscriminator.Context ctx = new LocalVariableDiscriminator.Context(injectionInfo, returnType, discriminator.isArgsOnly(), target, targetInsn);
        int local = discriminator.findLocal(ctx);
        return Pair.of(targetInsn, local);
    }

    private static class MethodCallInterpreter extends SourceInterpreter {
        private final MethodInsnNode targetInsn;
        private List<AbstractInsnNode> targetArgs;

        public MethodCallInterpreter(MethodInsnNode targetInsn) {
            super(Opcodes.ASM9);
            this.targetInsn = targetInsn;
        }

        @Nullable
        public List<AbstractInsnNode> getTargetArgs() {
            return this.targetArgs;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn == this.targetInsn && this.targetArgs == null) {
                List<AbstractInsnNode> targetArgs = values.stream()
                    .map(v -> v.insns.size() == 1 ? v.insns.iterator().next() : null)
                    .toList();
                if (!targetArgs.contains(null)) {
                    this.targetArgs = targetArgs;
                }
            }
            return super.naryOperation(insn, values);
        }
    }
}
