package org.sinytra.adapter.patch.api;

public interface MethodTransformFilter {
    boolean test(MethodContext methodContext);
}
