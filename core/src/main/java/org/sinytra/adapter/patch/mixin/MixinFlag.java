package org.sinytra.adapter.patch.mixin;

public enum MixinFlag {
    /**
     * New injection point must have the same return type
     */
    RETURN_TYPE_SENSITIVE,
    /**
     * Try to keep at target same when moving target methods
     */
    AT_TARGET_SENSITIVE,
    /**
     * The injection target is an indexed variable
     */
    TARGETS_VARIABLE,
    /**
     * The first mixin method param is an instance variable
     */
    ACCEPTS_INSTANCE
}
