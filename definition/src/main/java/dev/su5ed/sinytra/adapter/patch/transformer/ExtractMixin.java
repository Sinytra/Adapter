package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public record ExtractMixin(String targetClass) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.WRAP_OPERATION);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        // Sanity check
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (!isStatic && insn instanceof VarInsnNode varInsn && varInsn.var == 0
                || insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name)
                || insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name)
            ) {
                // We can't move methods that access their class instance
                return Patch.Result.PASS;
            }
        }
        // Get or generate new mixin class
        ClassNode generatedTarget = context.getEnvironment().getClassGenerator().getGeneratedMixinClass(classNode, this.targetClass);
        // Add mixin method from original to generated class
        generatedTarget.methods.add(methodNode);
        // Remove original method
        context.postApply(() -> classNode.methods.remove(methodNode));
        if (!isStatic && methodNode.localVariables != null) {
            methodNode.localVariables.stream().filter(l -> l.index == 0).findFirst().ifPresent(lvn -> {
                lvn.desc = Type.getObjectType(generatedTarget.name).getDescriptor();
            });
        }
        return Patch.Result.APPLY;
    }
}
