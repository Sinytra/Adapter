package org.sinytra.adapter.transform.patch;

import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.function.Predicate.isEqual;
import static org.sinytra.adapter.patch.config.key.ControlKeys.*;

public class MethodPatchBuilderImpl implements MethodPatchBuilder {
    private final ConfigurationMatcher.Builder matcher = ConfigurationMatcher.builder();
    private final MutableConfiguration config = MutableConfiguration.create();
    private BiConsumer<Configuration, MutableConfiguration> configCompleter = (a, b) -> {};
    private final List<MethodTransformer> transforms = new ArrayList<>();

    @Override
    public MethodPatch build() {
        ConfigurationMatcher finalMatcher = this.matcher.build();
        return new MethodPatchImpl(finalMatcher, this.config, this.configCompleter, this.transforms);
    }

    @Override
    public MethodPatchBuilder targetMixinType(String annotationDesc) {
        this.matcher.match(MIXIN_TYPE, isEqual(annotationDesc));
        return this;
    }

    @Override
    public MethodPatchBuilder targetClass(String... targets) {
        Stream.of(targets).forEach(t -> this.matcher.match(TARGET_CLASS, isEqual(t)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetMethod(String... targets) {
        Stream.of(targets)
            .map(t -> MethodQualifier.parse(t).orElseThrow())
            .forEach(t -> this.matcher.match(MixinKeys.TARGET_METHOD, t::matches));
        return this;
    }

    @Override
    public MethodPatchBuilder targetInjectionPoint(String target) {
        this.matcher.match(MixinKeys.TARGET_AT, t -> target.equals(t.getTarget().orElse(null)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetInjectionPoint(String value, String target) {
        this.matcher.match(MixinKeys.TARGET_AT, t -> value.equals(t.getValue()) && target.equals(t.getTarget().orElse(null)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetConstant(double doubleValue) {
        this.matcher.match(MixinKeys.TARGET_CONSTANT, c -> {
            // TODO OR check at const value
            Optional<Double> opt = c.doubleValue();
            return opt.isPresent() && opt.get() == doubleValue;
        });
        return this;
    }

    @Override
    public MethodPatchBuilder targetField(String target) {
        // TODO Field targets
        return this;
    }

    @Override
    public MethodPatchBuilder extractMixin(String targetClass) {
        this.config.setTargetClass(targetClass);
        return this;
    }

    @Override
    public MethodPatchBuilder modifyTarget(String method) {
        MethodQualifier qualifier = MethodQualifier.parse(method).orElseThrow();
        this.config.setTargetMethod(qualifier);
        return this;
    }

    @Override
    public MethodPatchBuilder modifyInjectionPoint(String target) {
        // AtData requires value which must be copied at transform time
        this.configCompleter = this.configCompleter.andThen((clean, dirty) ->
            dirty.setAtData(clean.getAtData().withTarget(target)));
        return this;
    }

    @Override
    public MethodPatchBuilder modifyInjectionPoint(String value, String target) {
        // Resolve dynamically to keep old values
        this.configCompleter = this.configCompleter.andThen((clean, dirty) ->
            dirty.setAtData(clean.getAtData().withValue(value).withTarget(target)));
        return this;
    }

    @Override
    public MethodPatchBuilder replaceInjectionPoint(String value, String target) {
        this.config.setAtData(AtData.create(value, target));
        return this;
    }

    @Override
    public MethodPatchBuilder modifyParams(UnaryOperator<MethodParameters> op) {
        this.configCompleter = this.configCompleter.andThen((clean, dirty) -> {
           MethodParameters params = clean.getParameters().copy();
           MethodParameters newParams = op.apply(params);
           dirty.setParameters(newParams);
        });
        return this;
    }

    @Override
    public MethodPatchBuilder modifyStatic(boolean isStatic) {
        this.config.setProperty(SpecialKeys.STATIC, isStatic);
        return this;
    }

    @Override
    public MethodPatchBuilder modifyMixinType(String newType) {
        this.config.setMixinType(newType);
        return this;
    }

    @Override
    public MethodPatchBuilder disable() {
        this.config.setShouldDelete(true);
        return this;
    }

    @Override
    public MethodPatchBuilder divertRedirector(Consumer<InstructionAdapter> patcher) {
        this.config.setProperty(SpecialKeys.REDIRECT_ADAPTER, patcher);
        return this;
    }

    @Override
    public MethodPatchBuilder transform(MethodTransformer transformer) {
        this.transforms.add(transformer);
        return this;
    }
}
