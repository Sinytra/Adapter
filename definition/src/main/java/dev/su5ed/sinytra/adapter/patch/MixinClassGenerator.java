package dev.su5ed.sinytra.adapter.patch;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
 
public class MixinClassGenerator {
    private final Map<String, GeneratedClass> generatedMixinClasses = new HashMap<>();

    public record GeneratedClass(String originalName, String generatedName, ClassNode node) {
    }

    public Map<String, GeneratedClass> getGeneratedMixinClasses() {
        return generatedMixinClasses;
    }

    public ClassNode getGeneratedMixinClass(ClassNode original, String targetClass) {
        int lastSeparator = original.name.lastIndexOf('/');
        String pkg = original.name.substring(0, lastSeparator + 1);
        String[] parts = targetClass.split("/");
        String className = pkg + "adapter_generated_" + parts[parts.length - 1];
        GeneratedClass generatedClass = this.generatedMixinClasses.computeIfAbsent(className, s -> {
            ClassNode node = generateMixinClass(s, targetClass);
            return new GeneratedClass(original.name, node.name, node);
        });
        return generatedClass.node();
    }

    private ClassNode generateMixinClass(String className, String targetClass) {
        ClassNode targetNode;
        try {
            targetNode = MixinService.getService().getBytecodeProvider().getClassNode(targetClass);
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalArgumentException("Target class " + targetClass + " not found", e);
        }

        ClassNode node = new ClassNode();
        boolean itf = (targetNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
        int flags = itf ? Opcodes.ACC_INTERFACE : Opcodes.ACC_FINAL;
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | flags, className, null, "java/lang/Object", null);
        AnnotationVisitor mixinAnn = node.visitAnnotation(PatchInstance.MIXIN_ANN, false);
        {
            AnnotationVisitor valueArray = mixinAnn.visitArray("value");
            valueArray.visit(null, Type.getType(Type.getObjectType(targetClass).getDescriptor()));
            valueArray.visitEnd();
        }
        mixinAnn.visitEnd();
        return node;
    }
}
