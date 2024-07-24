package org.sinytra.adapter.patch.api;

import org.objectweb.asm.tree.ClassNode;

public interface PatchAuditTrail {
    void prepareMethod(MethodContext methodContext);

    void recordAudit(Object transform, ClassNode classNode, String message, Object... args);

    void recordAudit(Object transform, MethodContext methodContext, String message, Object... args);

    void recordResult(MethodContext methodContext, Match match);

    String getCompleteReport();

    boolean hasFailingMixins();

    enum Match {
        NONE,
        PARTIAL,
        FULL;

        public Match or(Match other) {
            if (this == NONE && other != NONE) {
                return other;
            }
            if (this == PARTIAL && other == FULL) {
                return FULL;
            }
            return this;
        }
    }
}
