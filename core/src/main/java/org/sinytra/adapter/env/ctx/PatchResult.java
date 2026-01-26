package org.sinytra.adapter.env.ctx;

public enum PatchResult {
    PASS,
    APPLY,
    COMPUTE_FRAMES;

    public PatchResult or(PatchResult other) {
        if (this == PASS && other != PASS) {
            return other;
        }
        if (this == APPLY && other == COMPUTE_FRAMES) {
            return COMPUTE_FRAMES;
        }
        return this;
    }
}
