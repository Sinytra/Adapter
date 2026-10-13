package org.sinytra.adapter.pipeline.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.api.ClassPatchContext;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.ann.RefMapper;

public interface PatchContext extends ClassPatchContext, RefMapper {
    @Nullable
    ClassTarget classTarget();

    void postApply(Runnable consumer);

    void onRollback(Runnable undo);
}
