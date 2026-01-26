package org.sinytra.adapter.patch;

import org.sinytra.adapter.transform.PipelineMethodTransformer;
import org.sinytra.adapter.types.FieldAccessorTypeTransformer;
import org.sinytra.adapter.transform.MethodTransformer;
import org.sinytra.adapter.transform.cls.DynamicAnonClassIndexPatch;
import org.sinytra.adapter.transform.cls.DynamicAnonymousShadowFieldTypePatch;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.adapter.transform.patch.MethodPatchTransformer;
import org.sinytra.adapter.transform.preprocess.LocalCaptureUpgradeTransformer;
import org.sinytra.adapter.transform.ClassTransformer;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;

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
