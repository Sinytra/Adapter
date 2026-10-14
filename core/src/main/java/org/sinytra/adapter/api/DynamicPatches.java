package org.sinytra.adapter.api;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.sinytra.adapter.pipeline.PipelineMethodTransformer;
import org.sinytra.adapter.pipeline.transform.AnonymousTargetTransformer;
import org.sinytra.adapter.pipeline.transform.LocalCaptureUpgradeTransformer;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;

import java.util.List;

public final class DynamicPatches {
    public static final List<ClassTransformer> CLASS_PATCHES = List.of(
        new AnonymousTargetTransformer(),
        new FieldTypeUsageTransformer()
    );

    public static Multimap<TxPhase, MethodTransformer> methodTransformers(List<MethodPatch> patches) {
        return ImmutableMultimap.of(
            TxPhase.EARLY, new LocalCaptureUpgradeTransformer(),
            TxPhase.VALIDATED, new PipelineMethodTransformer(patches, false) 
        );
    }

    private DynamicPatches() {}
}
