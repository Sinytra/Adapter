package org.sinytra.adapter.next.pipeline;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.ConfigurationImpl;

import java.util.Optional;
import java.util.function.Consumer;

public record TxResultInstance(TxResult type, @Nullable Configuration patch) {
    private static final TxResultInstance FAIL = new TxResultInstance(TxResult.FAIL, null);
    private static final TxResultInstance PASS = new TxResultInstance(TxResult.PASS, null);

    public static TxResultInstance success(Configuration patch) {
        return new TxResultInstance(TxResult.SUCCESS, patch);
    }

    public static TxResultInstance success(Consumer<Configuration> consumer) {
        Configuration patch = new ConfigurationImpl();
        consumer.accept(patch);
        return new TxResultInstance(TxResult.SUCCESS, patch);
    }

    public static TxResultInstance pass() {
        return PASS;
    }

    public static TxResultInstance fail() {
        return FAIL;
    }

    public Optional<Configuration> maybePatch() {
        return Optional.ofNullable(this.patch);
    }
}
