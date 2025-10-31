package org.sinytra.adapter.next.analysis;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class WeighedDisambiguation<R> {
    @Nullable
    private final BiPredicate<R, R> resultsEqual;
    private final List<Supplier<List<R>>> resultSuppliers;

    private WeighedDisambiguation(BiPredicate<R, R> resultsEqual, List<Supplier<List<R>>> resultSuppliers) {
        this.resultsEqual = resultsEqual;
        this.resultSuppliers = resultSuppliers;
    }

    @Nullable
    public R findBestMatch() {
        return this.resultSuppliers.stream()
            .map(Supplier::get)
            .flatMap(r -> disambiguate(r).stream())
            .findFirst()
            .orElse(null);
    }

    public Optional<R> disambiguate(List<R> results) {
        return results.size() == 1
            || !results.isEmpty() && this.resultsEqual != null && AdapterUtil.allElementsEqual(results, this.resultsEqual)
            ? Optional.of(results.getFirst())
            : Optional.empty();
    }

    public static <R> Builder<R> builder() {
        return new Builder<>();
    }

    public static class Builder<R> {
        private final List<Supplier<List<R>>> resultSuppliers = new ArrayList<>();
        private BiPredicate<R, R> resultsEqual;

        public Builder<R> resultsEqual(BiPredicate<R, R> resultsEqual) {
            this.resultsEqual = resultsEqual;
            return this;
        }

        public Builder<R> match(Supplier<List<R>> supplier) {
            this.resultSuppliers.add(supplier);
            return this;
        }

        public WeighedDisambiguation<R> build() {
            return new WeighedDisambiguation<>(this.resultsEqual, this.resultSuppliers);
        }
    }
}
