package org.sinytra.adapter.next.transform.patch;

import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.transform.MethodTransformer;

import java.util.List;

public interface MethodPatch {
    ConfigurationMatcher matcher();

    Configuration configuration();

    List<MethodTransformer> transforms();

    static MethodPatchBuilder builder() {
        return new MethodPatchBuilderImpl();
    }
}
