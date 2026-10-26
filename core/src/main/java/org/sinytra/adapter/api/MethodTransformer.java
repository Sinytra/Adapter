package org.sinytra.adapter.api;

public interface MethodTransformer {
    PatchResult apply(MethodPatchContext context);
}
