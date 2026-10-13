package org.sinytra.adapter.analysis.injection;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.ann.AtData;

public final class InjectionQuery {
    public enum Source {
        /// The mixin's own @At
        CURRENT,
        /// The supplied @At
        AT,
        /// The supplied @At within the mixin's slice
        SLICE_BOUNDARY,
        /// The mixin's @Constant
        CONSTANT
    }

    private final Source source;
    @Nullable
    private final AtData at;
    private final boolean keepShift;
    private final boolean ignoreSlice;

    private InjectionQuery(Source source, @Nullable AtData at, boolean keepShift, boolean ignoreSlice) {
        this.source = source;
        this.at = at;
        this.keepShift = keepShift;
        this.ignoreSlice = ignoreSlice;
    }

    /// {@return a query for the mixin's own @At}
    public static InjectionQuery current() {
        return new InjectionQuery(Source.CURRENT, null, false, false);
    }

    /// {@return a query for the given @At}
    public static InjectionQuery of(AtData at) {
        return new InjectionQuery(Source.AT, at, false, false);
    }

    /// {@return a query for an @At within the mixin's slice, including shift}
    public static InjectionQuery sliceBoundary(AtData at) {
        return new InjectionQuery(Source.SLICE_BOUNDARY, at, true, true);
    }

    /// {@return a query for the mixin's @Constant}
    public static InjectionQuery constant() {
        return new InjectionQuery(Source.CONSTANT, null, false, false);
    }


    /// {@return this query with the injection point's shift applied}
    public InjectionQuery keepingShift() {
        return new InjectionQuery(this.source, this.at, true, this.ignoreSlice);
    }

    /// {@return this query ignoring the mixin's slice}
    public InjectionQuery ignoringSlice() {
        return new InjectionQuery(this.source, this.at, this.keepShift, true);
    }

    public Source source() {
        return this.source;
    }

    @Nullable
    public AtData at() {
        return this.at;
    }


    public boolean keepShift() {
        return this.keepShift;
    }

    public boolean ignoreSlice() {
        return this.ignoreSlice;
    }
}
