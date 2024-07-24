package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.adapter.patch.transformer.operation.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class DynFixSplitMethod implements DynamicFixer<DynFixSplitMethod.Data> {
    private static final String DEPRECATED = "Ljava/lang/Deprecated;";

    public record Data() {}

    @Nullable
    @Override
    public DynFixSplitMethod.Data prepare(MethodContext methodContext) {
        if (methodContext.hasInjectionPointValue("INVOKE") && methodContext.findCleanInjectionTarget() != null && methodContext.findDirtyInjectionTarget() != null) {
            return new Data();
        }
        return null;
    }

    @Override
    @Nullable
    public FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, Data data) {
        MethodNode cleanTargetMethod = methodContext.findCleanInjectionTarget().methodNode();
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        MethodNode dirtyTargetMethod = methodContext.findDirtyInjectionTarget().methodNode();

        // Check that a Deprecated annotation was added to the dirty method 
        if (AdapterUtil.hasAnnotation(cleanTargetMethod.visibleAnnotations, DEPRECATED) || !AdapterUtil.hasAnnotation(dirtyTargetMethod.visibleAnnotations, DEPRECATED)) {
            return null;
        }

        // Iterate over isns, leave out first and last elements
        // Collect method invocations
        // All labels must be finalized by a method invocation to pass
        List<MethodNode> invocations = new ArrayList<>();
        for (int i = 1; i < dirtyTargetMethod.instructions.size() - 1; i++) {
            AbstractInsnNode insn = dirtyTargetMethod.instructions.get(i);
            if (insn instanceof LabelNode) {
                AbstractInsnNode previous = insn.getPrevious(); 
                if (previous instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(dirtyTargetClass.name)) {
                    MethodNode method = dirtyTargetClass.methods.stream().filter(m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElseThrow();
                    invocations.add(method);
                } else if (previous == null || !OpcodeUtil.isReturnOpcode(previous.getOpcode())) {
                    return null;
                }
            }
        }

        List<MethodNode> candidates = findInsnsCalls(invocations, methodContext);

        // Attempt to find matching insns in lambdas
        if (candidates.isEmpty()) {
            List<MethodNode> nestedLambdas = invocations.stream()
                .flatMap(m -> MethodCallAnalyzer.findLambdasInMethod(dirtyTargetClass, m, null).stream())
                .flatMap(s -> MethodCallAnalyzer.findMethodByUniqueName(dirtyTargetClass, s).stream())
                .toList();
            candidates = findInsnsCalls(nestedLambdas, methodContext);
        }

        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst();
            String newTarget = method.name + method.desc;
            methodContext.recordAudit(this, "Adjusting split method target to %s", newTarget);
            return FixResult.of(new ModifyInjectionTarget(List.of(newTarget)).apply(methodContext), PatchAuditTrail.Match.FULL);
        }

        return null;
    }

    private static List<MethodNode> findInsnsCalls(List<MethodNode> methods, MethodContext methodContext) {
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        return methods.stream().filter(method -> !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(dirtyTargetClass, method)).isEmpty()).toList();
    }
}
