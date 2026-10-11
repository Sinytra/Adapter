package org.sinytra.adapter.pipeline.processor;

import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.PropertyKey;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyProcessor implements Processor {
    private static final List<PropertyKey<?>> ACCEPTED_KEYS = List.of(
        MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT,
        MixinKeys.ORDINAL, MixinKeys.INDEX, MixinKeys.SLICE, MixinKeys.SLICES, MixinKeys.ARGS_ONLY
    );

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        AnnotationHandle handle = context.methodAnnotation();

        MutableConfiguration subConfig = dirty.copyClean();
        ACCEPTED_KEYS.forEach(k -> dirty.getProperty(k)
            .ifPresent(v -> subConfig.setProperty((PropertyKey) k, v)));

        subConfig.getProperties().forEach((k, v) ->
            context.recordCtxAudit("Set property '%s' to: %s", k.name(), v));

        // Apply new props
        subConfig.apply(handle);

        // Delete removed props
        Set<String> removing = recipe.clean().getProperties().keySet().stream()
            .filter(ACCEPTED_KEYS::contains)
            .filter(Predicate.not(dirty::hasProperty))
            .map(PropertyKey::name)
            .collect(Collectors.toSet());
        handle.removeValues(removing.toArray(String[]::new));

        return TxResult.SUCCESS;
    }
}
