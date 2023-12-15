package dev.su5ed.sinytra.adapter.patch.transformer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.InjectionPointMatcher;
import dev.su5ed.sinytra.adapter.patch.serialization.MethodTransformSerialization;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.List;

public record InjectionPointTransformerFilter(MethodTransform wrapped, List<InjectionPointMatcher> excludedInjectionPoints) implements MethodTransform {
    public static final Codec<InjectionPointTransformerFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MethodTransformSerialization.METHOD_TRANSFORM_CODEC.fieldOf("wrapped").forGetter(InjectionPointTransformerFilter::wrapped),
        InjectionPointMatcher.CODEC.listOf().fieldOf("excludedInjectionPoints").forGetter(InjectionPointTransformerFilter::excludedInjectionPoints)
    ).apply(instance, InjectionPointTransformerFilter::new));

    public static InjectionPointTransformerFilter create(MethodTransform wrapped, List<String> excludedInjectionPoints) {
        return new InjectionPointTransformerFilter(wrapped, excludedInjectionPoints.stream().map(str -> new InjectionPointMatcher((String) null, str)).toList());
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return this.wrapped.getAcceptedAnnotations();
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        String injectionPoint = methodContext.injectionPointAnnotation().<String>getValue("target")
            .map(AnnotationValueHandle::get)
            .map(context::remap)
            .orElse(null);
        if (injectionPoint != null && this.excludedInjectionPoints.stream().anyMatch(matcher -> matcher.test(null, injectionPoint))) {
            return Patch.Result.PASS;
        }
        return this.wrapped.apply(classNode, methodNode, methodContext, context);
    }
}
