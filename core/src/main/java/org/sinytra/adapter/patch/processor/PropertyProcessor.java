package org.sinytra.adapter.patch.processor;

import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyKey;
import org.sinytra.adapter.patch.config.key.MixinKeys;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertyProcessor implements Processor {
    private static final Set<PropertyKey<?>> ACCEPTED_KEYS = Set.of(
        MixinKeys.TARGET_METHOD, MixinKeys.TARGET_AT, MixinKeys.TARGET_CONSTANT,
        MixinKeys.ORDINAL, MixinKeys.INDEX, MixinKeys.SLICE, MixinKeys.ARGS_ONLY
    );

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        AnnotationHandle handle = context.methodAnnotation();

        MutableConfiguration subConfig = dirty.copyClean();
        ACCEPTED_KEYS.forEach(k -> dirty.getProperty(k)
            .ifPresent(v -> subConfig.setProperty((PropertyKey) k, v)));

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
