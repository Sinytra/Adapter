package org.sinytra.adapter.analysis.injection;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.ann.ConstantData;

public final class InjectionQuery {
    public enum Source {
        /// The mixin's own @At
        CURRENT,
        /// The supplied @At
        AT,
        /// The supplied @At within the mixin's slice
        SLICE_BOUNDARY,
        /// The mixin's @Constant or the supplied one
        CONSTANT
    }

    private final Source source;
    @Nullable
    private final AtData at;
    @Nullable
    private final ConstantData constant;
    private final boolean keepShift;
    private final boolean ignoreSlice;

    private InjectionQuery(Source source, @Nullable AtData at, @Nullable ConstantData constant, boolean keepShift, boolean ignoreSlice) {
        this.source = source;
        this.at = at;
        this.constant = constant;
        this.keepShift = keepShift;
        this.ignoreSlice = ignoreSlice;
    }

    /// {@return a query for the mixin's own @At}
    public static InjectionQuery current() {
        return new InjectionQuery(Source.CURRENT, null, null, false, false);
    }

    /// {@return a query for the given @At}
    public static InjectionQuery of(AtData at) {
        return new InjectionQuery(Source.AT, at, null, false, false);
    }

    /// {@return a query for an @At within the mixin's slice, including shift}
    public static InjectionQuery sliceBoundary(AtData at) {
        return new InjectionQuery(Source.SLICE_BOUNDARY, at, null, true, true);
    }

    /// {@return a query for the mixin's @Constant}
    public static InjectionQuery constant() {
        return new InjectionQuery(Source.CONSTANT, null, null, false, false);
    }

    /// {@return a query for the given @Constant}
    public static InjectionQuery of(ConstantData constant) {
        return new InjectionQuery(Source.CONSTANT, null, constant, false, false);
    }


    /// {@return this query with the injection point's shift applied}
    public InjectionQuery keepingShift() {
        return new InjectionQuery(this.source, this.at, this.constant, true, this.ignoreSlice);
    }

    /// {@return this query ignoring the mixin's slice}
    public InjectionQuery ignoringSlice() {
        return new InjectionQuery(this.source, this.at, this.constant, this.keepShift, true);
    }

    public Source source() {
        return this.source;
    }

    @Nullable
    public AtData at() {
        return this.at;
    }

    @Nullable
    public ConstantData constantData() {
        return this.constant;
    }


    public boolean keepShift() {
        return this.keepShift;
    }

    public boolean ignoreSlice() {
        return this.ignoreSlice;
    }
}
