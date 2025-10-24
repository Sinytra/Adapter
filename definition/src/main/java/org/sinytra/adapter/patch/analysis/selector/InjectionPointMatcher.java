package org.sinytra.adapter.patch.analysis.selector;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Optional;
import java.util.function.Predicate;

public record InjectionPointMatcher(@Nullable String value, TargetMatcher target) {
    public InjectionPointMatcher(@Nullable String value, String target) {
        this(value, TargetMatcher.create(target));
    }

    public boolean test(String value, String target) {
        return (this.value == null || this.value.equals(value)) && this.target.test(target);
    }

    interface TargetMatcher extends Predicate<String> {
        String target();

        static TargetMatcher create(String target) {
            return Optional.of(target)
                .<TargetMatcher>flatMap(str -> MethodQualifier.create(str)
                    .filter(q -> q.name() != null)
                    .map(q -> new MethodQualifierMatcher(str, q)))
                .orElseGet(() -> new SimpleTargetMatcher(AdapterUtil.maybeRemapFieldRef(target)));
        }
    }

    private record SimpleTargetMatcher(String target) implements TargetMatcher {
        @Override
        public boolean test(String s) {
            return this.target.equals(s);
        }
    }

    private record MethodQualifierMatcher(String target, MethodQualifier qualifier) implements TargetMatcher {
        @Override
        public boolean test(String s) {
            return MethodQualifier.create(s).map(this.qualifier::matches).orElse(false);
        }
    }
}
