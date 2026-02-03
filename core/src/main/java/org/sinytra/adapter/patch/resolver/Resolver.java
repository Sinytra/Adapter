package org.sinytra.adapter.patch.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;

import java.util.Optional;

public interface Resolver {
    enum ResultType {
        REPLACE,
        SUCCESS,
        PASS,
        FAIL
    }

    record ResolutionResult(ResultType type, @Nullable Configuration patch) {
        public static ResolutionResult fail() {
            return new ResolutionResult(ResultType.FAIL, null);
        }

        public static ResolutionResult pass() {
            return new ResolutionResult(ResultType.PASS, null);
        }

        public static ResolutionResult success(Configuration patch) {
            return new ResolutionResult(ResultType.SUCCESS, patch);
        }

        public static ResolutionResult replace(Configuration patch) {
            return new ResolutionResult(ResultType.REPLACE, patch);
        }

        public Optional<Configuration> maybePatch() {
            return Optional.ofNullable(this.patch);
        }
    }

    @NotNull
    ResolutionResult resolve(MixinContext context, Recipe recipe);
}
