package org.sinytra.adapter.next.flow;

import org.sinytra.adapter.next.pipeline.PipelineMethodTransformer;
import org.sinytra.adapter.next.types.FieldAccessorTypeTransformer;
import org.sinytra.adapter.next.transform.MethodTransformer;
import org.sinytra.adapter.next.transform.cls.DynamicAnonClassIndexPatch;
import org.sinytra.adapter.next.transform.cls.DynamicAnonymousShadowFieldTypePatch;
import org.sinytra.adapter.next.transform.patch.MethodPatch;
import org.sinytra.adapter.next.transform.patch.MethodPatchTransformer;
import org.sinytra.adapter.next.transform.preprocess.LocalCaptureUpgradeTransformer;
import org.sinytra.adapter.next.transform.ClassTransformer;
import org.sinytra.adapter.next.types.FieldTypeUsageTransformer;

import java.util.List;

public class DynamicPatches {
    public static final List<ClassTransformer> CLASS_PATCHES = List.of(
        new DynamicAnonClassIndexPatch(),
        new DynamicAnonymousShadowFieldTypePatch(),
        new FieldTypeUsageTransformer()
    );

    public static List<MethodTransformer> methodTransformers(List<MethodPatch> patches) {
        return List.of(
            new FieldAccessorTypeTransformer(), // Interface mixin only
            new LocalCaptureUpgradeTransformer(),
            new MethodPatchTransformer(patches),
            new PipelineMethodTransformer()
        );
    }
}
