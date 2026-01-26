package org.sinytra.adapter.transform.patch;

import com.google.common.collect.ImmutableMap;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyKey;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class ConfigurationMatcher {
    private final Map<PropertyKey<?>, Predicate<?>> matchers;

    private ConfigurationMatcher(Map<PropertyKey<?>, Predicate<?>> matchers) {
        this.matchers = ImmutableMap.copyOf(matchers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean match(PropertyContainer container) {
        for (Map.Entry<PropertyKey<?>, Predicate<?>> entry : this.matchers.entrySet()) {
            Object value = container.getProperty(entry.getKey());
            if (value == null || !((Predicate) entry.getValue()).test(value)) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<PropertyKey<?>, Predicate<?>> matchers = new HashMap<>();

        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> Builder match(PropertyKey<T> prop, Predicate<T> predicate) {
            this.matchers.compute(prop, (k, v) -> v == null ? predicate : v.or((Predicate) predicate));
            return this;
        }

        public ConfigurationMatcher build() {
            return new ConfigurationMatcher(this.matchers);
        }
    }
}
