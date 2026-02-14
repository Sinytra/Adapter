package org.sinytra.adapter.transform.patch;

import com.google.common.collect.ImmutableList;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyKey;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ConfigurationMatcher {
    private final List<SubMatcher> matchers;

    private ConfigurationMatcher(List<SubMatcher> matchers) {
        this.matchers = ImmutableList.copyOf(matchers);
    }

    public boolean match(PropertyContainer container) {
        for (SubMatcher subMatcher : this.matchers) {
            if (!subMatcher.match(container)) {
                return false;
            }
        }
        return true;
    }

    public interface SubMatcher {
        boolean match(PropertyContainer container);
    }

    private record SingleMatcher<T>(PropertyKey<T> key, Predicate<T> predicate) implements SubMatcher {
        private SingleMatcher(PropertyKey<T> key, Predicate<T> predicate) {
            this.key = Objects.requireNonNull(key);
            this.predicate = Objects.requireNonNull(predicate);
        }

        @Override
        public boolean match(PropertyContainer container) {
            T value = container.getProperty(this.key).orElse(null);
            return value != null && this.predicate.test(value);
        }
    }

    private record OrMatcher(List<SubMatcher> matchers) implements SubMatcher {
        @Override
        public boolean match(PropertyContainer container) {
            for (SubMatcher matcher : this.matchers) {
                if (matcher.match(container)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<PropertyKey<?>, Predicate<?>> singleMatchers = new HashMap<>();
        private final List<SubMatcher> matchers = new ArrayList<>();

        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> Builder match(PropertyKey<T> prop, Predicate<T> predicate) {
            this.singleMatchers.compute(prop, (k, v) -> v == null ? predicate : v.or((Predicate) predicate));
            return this;
        }

        public Builder match(SubMatcher subMatcher) {
            this.matchers.add(subMatcher);
            return this;
        }

        public Builder or(Consumer<Builder> subBuilder) {
            Builder sub = new Builder();
            subBuilder.accept(sub);
            match(new OrMatcher(sub.buildMatchers()));
            return this;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public List<SubMatcher> buildMatchers() {
            List<SubMatcher> all = new ArrayList<>();
            this.singleMatchers.forEach((key, pred) -> {
                SingleMatcher matcher = new SingleMatcher(key, pred);
                all.addFirst(matcher);
            });
            all.addAll(this.matchers);
            return all;
        }

        public ConfigurationMatcher build() {
            return new ConfigurationMatcher(buildMatchers());
        }
    }
}
