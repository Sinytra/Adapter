package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record ExtractMixin(String targetClass) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.WRAP_OPERATION, Patch.MODIFY_CONST, Patch.MODIFY_ARG, Patch.INJECT, Patch.REDIRECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        // Sanity check
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name) && !isShadowMember(getVisibleFieldAnnotations(classNode, finsn.name))
                || insn instanceof MethodInsnNode minsn && minsn.owner.equals(classNode.name) && !isShadowMember(getVisibleMethodAnnotations(classNode, minsn.name, minsn.desc))
            ) {
                // We can't move methods that access their class instance
                return Patch.Result.PASS;
            }
        }
        ClassNode targetClass = AdapterUtil.getClassNode(this.targetClass);
        // Get or generate new mixin class
        ClassNode generatedTarget = context.getEnvironment().getClassGenerator().getGeneratedMixinClass(classNode, this.targetClass, targetClass != null ? targetClass.superName : null);
        context.getEnvironment().copyRefmap(classNode.name, generatedTarget.name);
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

    private static List<AnnotationNode> getVisibleFieldAnnotations(ClassNode cls, String name) {
        return cls.fields.stream().filter(f -> f.name.equals(name)).map(f -> f.visibleAnnotations).findFirst().orElse(List.of());
    }

    private static List<AnnotationNode> getVisibleMethodAnnotations(ClassNode cls, String name, String desc) {
        return cls.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).map(f -> f.visibleAnnotations).findFirst().orElse(List.of());
    }

    private static boolean isShadowMember(List<AnnotationNode> annotations) {
        return annotations.stream().anyMatch(an -> an.desc.equals(AdapterUtil.SHADOW_ANN));
    }
}
