package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public record RedirectAccessor(String value) implements MethodTransform {
    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.ACCESSOR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        AnnotationValueHandle<String> valueHandle = (AnnotationValueHandle<String>) annotationValues.get("value");
        if (valueHandle == null) {
            if (annotation.values == null) {
                annotation.values = new ArrayList<>();
            }
            annotation.values.add("value");
            annotation.values.add(this.value);
        }
        else {
            valueHandle.set(this.value);
        }
        return Patch.Result.APPLY;
    }
}
