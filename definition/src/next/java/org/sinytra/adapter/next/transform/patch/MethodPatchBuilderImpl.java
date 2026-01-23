package org.sinytra.adapter.next.transform.patch;

import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.config.SpecialKeys;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.function.Predicate.isEqual;
import static org.sinytra.adapter.next.pipeline.config.Keys.*;

public class MethodPatchBuilderImpl implements MethodPatchBuilder {
    private final ConfigurationMatcher.Builder matcher = ConfigurationMatcher.builder();
    private final MutableConfiguration config = MutableConfiguration.create();
    private final List<MethodTransformer> transforms = new ArrayList<>();

    public MethodPatchImpl build() {
        ConfigurationMatcher finalMatcher = this.matcher.build();
        return new MethodPatchImpl(finalMatcher, this.config, this.transforms);
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
            .map(t -> MethodQualifier.create(t).orElseThrow())
            .forEach(t -> this.matcher.match(TARGET_METHOD, q -> q.matches(t)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetInjectionPoint(String target) {
        this.matcher.match(TARGET_AT, t -> target.equals(t.getTarget().orElse(null)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetInjectionPoint(String value, String target) {
        this.matcher.match(TARGET_AT, t -> value.equals(t.getValue()) && target.equals(t.getTarget().orElse(null)));
        return this;
    }

    @Override
    public MethodPatchBuilder targetConstant(double doubleValue) {
        this.matcher.match(TARGET_CONSTANT, c -> {
            // TODO OR check at const value
            OptionalDouble opt = c.doubleValue();
            return opt.isPresent() && opt.getAsDouble() == doubleValue;
        });
        return this;
    }

    @Override
    public MethodPatchBuilder targetField(String target) {
        // TODO
        return this;
    }

    @Override
    public MethodPatchBuilder extractMixin(String targetClass) {
        this.config.setTargetClass(targetClass);
        return this;
    }

    @Override
    public MethodPatchBuilder modifyTarget(String method) {
        MethodQualifier qualifier = MethodQualifier.create(method).orElseThrow();
        this.config.setTargetMethod(qualifier);
        return this;
    }

    @Override
    public MethodPatchBuilder modifyInjectionPoint(String target) {
        // TODO sort out the value placeholder
        this.config.setAtData(AtData.create("", target));
        return this;
    }

    @Override
    public MethodPatchBuilder modifyInjectionPoint(String value, String target) {
        // TODO How to not reset other values?
        this.config.setAtData(AtData.create(value, target));
        return this;
    }

    @Override
    public MethodPatchBuilder modifyInjectionPoint(String value, String target, boolean resetValues) {
        this.config.setAtData(AtData.create(value, target));
        return this;
    }

    @Override
    public MethodPatchBuilder modifyStatic(boolean isStatic) {
        // TODO Attribute
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
