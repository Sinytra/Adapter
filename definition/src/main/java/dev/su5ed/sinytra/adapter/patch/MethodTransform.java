package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface MethodTransform extends ClassTransform {
    default Codec<? extends MethodTransform> codec() {
        throw new UnsupportedOperationException("This transform is not serializable");
    }

    @Override
    default Result apply(ClassNode classNode) {
        return new Result(false, false);
    }

    default Collection<String> getAcceptedAnnotations() {
        return Set.of();
    }

    boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context);
}
