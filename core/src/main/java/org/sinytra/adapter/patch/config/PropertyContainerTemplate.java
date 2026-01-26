package org.sinytra.adapter.patch.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class PropertyContainerTemplate {
    private final Set<PropertyKey<?>> keys;
    private final List<Validator> constraints;

    private PropertyContainerTemplate(Set<PropertyKey<?>> keys, List<Validator> constraints) {
        this.keys = ImmutableSet.copyOf(keys);
        this.constraints = ImmutableList.copyOf(constraints);
    }

    public Set<PropertyKey<?>> getKeys() {
        return this.keys;
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
        return new Builder(this.keys, this.constraints);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<PropertyKey<?>> keys;
        private final List<Validator> constraints;

        public Builder() {
            this.keys = new HashSet<>();
            this.constraints = new ArrayList<>();
        }

        public Builder(Set<PropertyKey<?>> keys, List<Validator> constraints) {
            this.keys = new HashSet<>(keys);
            this.constraints = new ArrayList<>(constraints);
        }

        public Builder keys(PropertyKey<?>... keys) {
            this.keys.addAll(List.of(keys));
            return this;
        }

        public Builder require(PropertyKey<?>... keys) {
            keys(keys);
            return addConstraint(c -> Stream.of(keys).allMatch(c::hasProperty));
        }

        public Builder requireOne(PropertyKey<?>... keys) {
            keys(keys);
            return addConstraint(c -> Stream.of(keys)
                .filter(c::hasProperty)
                .count() == 1);
        }

        public Builder addConstraint(Validator validator) {
            this.constraints.add(validator);
            return this;
        }

        public PropertyContainerTemplate build() {
            return new PropertyContainerTemplate(this.keys, this.constraints);
        }
    }

    public interface Validator {
        boolean validate(PropertyContainer container);
    }
}
