package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.api.MixinClassGenerator;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MixinClassGeneratorImpl implements MixinClassGenerator {
    private final Map<String, GeneratedClass> generatedMixinClasses = new ConcurrentHashMap<>();

    @Nullable
    public Map<String, GeneratedClass> getGeneratedMixinClasses() {
        return this.generatedMixinClasses;
    }

    @Override
    public ClassNode getOrGenerateMixinClass(ClassNode original, String targetClass, @Nullable String parent) {
        int lastSeparator = original.name.lastIndexOf('/');
        String pkg = original.name.substring(0, lastSeparator + 1);
        String[] parts = targetClass.split("/");
        String className = pkg + "adapter_generated_" + parts[parts.length - 1];
        GeneratedClass generatedClass;
        synchronized (this.generatedMixinClasses) {
            generatedClass = this.generatedMixinClasses.computeIfAbsent(className, s -> {
                ClassNode node = doGenerateMixinClass(s, targetClass, parent);
                return new GeneratedClass(original.name, node.name, node);
            });
        }
        return generatedClass.node();
    }

    private ClassNode doGenerateMixinClass(String className, String targetClass, @Nullable String parent) {
        ClassNode targetNode;
        try {
            targetNode = MixinService.getService().getBytecodeProvider().getClassNode(targetClass);
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalArgumentException("Target class " + targetClass + " not found", e);
        }

        ClassNode node = new ClassNode();
        boolean itf = (targetNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
        int flags = itf ? Opcodes.ACC_INTERFACE : Opcodes.ACC_FINAL;
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | flags, className, null, parent != null ? parent : "java/lang/Object", null);
        AnnotationVisitor mixinAnn = node.visitAnnotation(MixinConstants.MIXIN, false);
        {
            AnnotationVisitor valueArray = mixinAnn.visitArray("value");
            valueArray.visit(null, Type.getType(Type.getObjectType(targetClass).getDescriptor()));
            valueArray.visitEnd();
        }
        mixinAnn.visitEnd();
        return node;
    }
}
