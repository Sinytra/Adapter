package org.sinytra.adapter.next.pipeline.config;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PropertyContainerTemplate {
    private final List<Validator> constraints;

    private PropertyContainerTemplate(List<Validator> constraints) {
        this.constraints = ImmutableList.copyOf(constraints);
    }

    public boolean validate(PropertyContainer container) {
        for (Validator constraint : this.constraints) {
            if (!constraint.validate(container)) {
                return false;
            }
        }
        return true;
    }

    public Builder extend() {
        return new Builder(this.constraints);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Validator> constraints;

        public Builder() {
            this.constraints = new ArrayList<>();
        }

        public Builder(List<Validator> constraints) {
            this.constraints = new ArrayList<>(constraints);
        }

        public Builder require(PropertyKey<?>... keys) {
            return addConstraint(c -> Stream.of(keys).allMatch(c::hasProperty));
        }

        public Builder requireOne(PropertyKey<?>... keys) {
            return addConstraint(c -> Stream.of(keys)
                .filter(c::hasProperty)
                .count() == 1);
        }

        public Builder addConstraint(Validator validator) {
            this.constraints.add(validator);
            return this;
        }

        public PropertyContainerTemplate build() {
            return new PropertyContainerTemplate(this.constraints);
        }
    }

    public interface Validator {
        boolean validate(PropertyContainer container);
    }
}
