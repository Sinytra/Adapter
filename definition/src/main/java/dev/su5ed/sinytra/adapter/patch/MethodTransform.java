package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface MethodTransform {
    default Codec<? extends MethodTransform> codec() {
        throw new UnsupportedOperationException("This transform is not serializable");
    }

    default Collection<String> getAcceptedAnnotations() {
        return Set.of();
    }

    Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context);
}
