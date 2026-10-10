package org.sinytra.adapter.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.util.provider.ClassLookup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MixinClassGeneratorImpl implements MixinClassGenerator {
    private final Map<String, GeneratedClass> generatedMixinClasses = new ConcurrentHashMap<>();
    private final ClassLookup classLookup;

    public MixinClassGeneratorImpl(ClassLookup classLookup) {
        this.classLookup = classLookup;
    }

    @Nullable
    public Map<String, GeneratedClass> getGeneratedMixinClasses() {
        return this.generatedMixinClasses;
    }

    @Override
    public ClassNode getOrGenerateMixinClass(ClassNode original, String targetClass, @Nullable String parent) {
        int lastSeparator = original.name.lastIndexOf('/');
        String pkg = original.name.substring(0, lastSeparator + 1);
        String className = pkg + "adapter_generated_" + targetClass.replace('/', '_');
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
        ClassNode targetNode = this.classLookup.getClass(targetClass)
            .orElseThrow(() -> new IllegalArgumentException("Target class " + targetClass + " not found"));

        ClassNode node = new ClassNode();
        boolean itf = (targetNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
        int flags = itf ? Opcodes.ACC_INTERFACE : 0;
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | flags, className, null, parent != null ? parent : "java/lang/Object", null);
        AnnotationVisitor mixinAnn = node.visitAnnotation(MixinAnnotations.MIXIN, false);
        {
            AnnotationVisitor valueArray = mixinAnn.visitArray("value");
            valueArray.visit(null, Type.getType(Type.getObjectType(targetClass).getDescriptor()));
            valueArray.visitEnd();
        }
        mixinAnn.visitEnd();
        return node;
    }
}
