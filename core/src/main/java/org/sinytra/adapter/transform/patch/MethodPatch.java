package org.sinytra.adapter.transform.patch;

import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.transform.MethodTransformer;

import java.util.List;

public interface MethodPatch {
    ConfigurationMatcher matcher();

    Configuration configuration();

    List<MethodTransformer> transforms();

    static MethodPatchBuilder builder() {
        return new MethodPatchBuilderImpl();
    }
}
