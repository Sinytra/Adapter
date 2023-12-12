package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MockMixinRuntime;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public class DynamicModifyVarAtReturnPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.MODIFY_VAR);
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
        Pair<ClassNode, MethodNode> cleanTarget = methodContext.findCleanInjectionTarget();
        if (cleanTarget == null) {
            return Patch.Result.PASS;
        }
        Pair<ClassNode, MethodNode> dirtyTarget = methodContext.findDirtyInjectionTarget();
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
        MethodCallInterpreter interpreter = AdapterUtil.analyzeMethod(dirtyTarget.getSecond(), new MethodCallInterpreter(dirtyMinsn));
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
                LOGGER.info(MIXINPATCH, "Redirecting RETURN variable modifier to parameter {} of method call to {}", i, qualifier);
                MethodTransform transform = new ModifyMixinType(Patch.MODIFY_ARG, b -> b.sameTarget()
                    .injectionPoint("INVOKE", qualifier)
                    .putValue("index", index));
                result = transform.apply(classNode, methodNode, methodContext, context);
            }
        }

        return result;
    }

    private static Pair<AbstractInsnNode, Integer> getTargetPair(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, Pair<ClassNode, MethodNode> injectionTarget, int ordinal) {
        // Find injection point insn
        ClassNode targetClass = injectionTarget.getFirst();
        MethodNode targetMethod = injectionTarget.getSecond();
        List<AbstractInsnNode> targetInsns = methodContext.findInjectionTargetInsns(classNode, targetClass, methodNode, targetMethod, context);
        AbstractInsnNode targetInsn = targetInsns.get(ordinal == -1 ? targetInsns.size() - 1 : ordinal);
        // Find modified variable
        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(methodContext.methodAnnotation().unwrap());
        InjectionInfo injectionInfo = MockMixinRuntime.forInjectionInfo(classNode.name, targetClass.name, context.getEnvironment());
        Type returnType = Type.getReturnType(methodNode.desc);
        Target target = new Target(targetClass, targetMethod);
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
