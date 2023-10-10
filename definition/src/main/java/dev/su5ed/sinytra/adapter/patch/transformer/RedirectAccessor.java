package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Set;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record RedirectAccessor(String value) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.ACCESSOR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        AnnotationValueHandle<String> valueHandle = annotation.<String>getValue("value").orElse(null);
        if (valueHandle == null) {
            annotation.appendValue("value", this.value);
        }
        else {
            valueHandle.set(this.value);
        }
        LOGGER.info(MIXINPATCH, "Redirecting accessor {}.{} to field {}", classNode.name, methodNode.name, this.value);
        return Patch.Result.APPLY;
    }
}
