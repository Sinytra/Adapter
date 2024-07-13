package org.sinytra.adapter.patch.transformer.dynfix;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

/**
 * Handle cases where a single method is split into multiple smaller pieces.
 * For an example, see <code>net.minecraft.client.gui.Gui#renderPlayerHealth</code>
 */
public class DynFixSplitMethod implements DynamicFixer<DynFixSplitMethod.Data> {
    private static final String DEPRECATED = "Ljava/lang/Deprecated;";
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Data() {}

    @Nullable
    @Override
    public DynFixSplitMethod.Data prepare(MethodContext methodContext) {
        if (methodContext.hasInjectionPointValue("INVOKE")) {
            return new Data();
        }
        return null;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, Data data) {
        MethodNode cleanTargetMethod = methodContext.findCleanInjectionTarget().methodNode();
        ClassNode dirtyTargetClass = methodContext.findDirtyInjectionTarget().classNode();
        MethodNode dirtyTargetMethod = methodContext.findDirtyInjectionTarget().methodNode();

        // Check that a Deprecated annotation was added to the dirty method 
        if (AdapterUtil.hasAnnotation(cleanTargetMethod.visibleAnnotations, DEPRECATED) || !AdapterUtil.hasAnnotation(dirtyTargetMethod.visibleAnnotations, DEPRECATED)) {
            return Patch.Result.PASS;
        }

        // Iterate over isns, leave out first and last elements
        // Collect method invocations
        // All labels must be finalized by a method invocation to pass
        List<MethodNode> invocations = new ArrayList<>();
        for (int i = 1; i < dirtyTargetMethod.instructions.size() - 1; i++) {
            AbstractInsnNode insn = dirtyTargetMethod.instructions.get(i);
            if (insn instanceof LabelNode) {
                if (insn.getPrevious() instanceof MethodInsnNode methodInsn && methodInsn.owner.equals(dirtyTargetClass.name)) {
                    MethodNode method = dirtyTargetClass.methods.stream().filter(m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElseThrow();
                    invocations.add(method);
                } else {
                    return Patch.Result.PASS;
                }
            }
        }

        List<MethodNode> candidates = invocations.stream().filter(method -> !methodContext.findInjectionTargetInsns(new MethodContext.TargetPair(dirtyTargetClass, method)).isEmpty()).toList();
        if (candidates.size() == 1) {
            MethodNode method = candidates.getFirst();
            String newTarget = method.name + method.desc;
            LOGGER.debug(MIXINPATCH, "Adjusting split method target of {}.{} to {}", classNode.name, methodNode.name, newTarget);
            return new ModifyInjectionTarget(List.of(newTarget)).apply(classNode, methodNode, methodContext);
        }

        return Patch.Result.PASS;
    }
}
