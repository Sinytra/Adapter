package org.sinytra.adapter.patch.transformer.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MethodTransformFilter;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.analysis.selector.InjectionPointMatcher;

import java.util.List;

public record InjectionPointTransformerFilter(List<InjectionPointMatcher> excludedInjectionPoints) implements MethodTransformFilter {
    public static final Codec<InjectionPointTransformerFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        InjectionPointMatcher.CODEC.listOf().fieldOf("excludedInjectionPoints").forGetter(InjectionPointTransformerFilter::excludedInjectionPoints)
    ).apply(instance, InjectionPointTransformerFilter::new));

    public static InjectionPointTransformerFilter create(List<String> excludedInjectionPoints) {
        return new InjectionPointTransformerFilter(excludedInjectionPoints.stream().map(str -> new InjectionPointMatcher((String) null, str)).toList());
    }

    @Override
    public Codec<? extends MethodTransformFilter> codec() {
        return CODEC;
    }

    @Override
    public boolean test(MethodContext methodContext) {
        String injectionPoint = methodContext.injectionPointAnnotation().<String>getValue("target")
            .map(AnnotationValueHandle::get)
            .map(methodContext.patchContext()::remap)
            .orElse(null);
        return injectionPoint == null || this.excludedInjectionPoints.stream().noneMatch(matcher -> matcher.test(null, injectionPoint));
    }
}
