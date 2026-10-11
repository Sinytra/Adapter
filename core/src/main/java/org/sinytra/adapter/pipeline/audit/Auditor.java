package org.sinytra.adapter.pipeline.audit;

public interface Auditor {
    void recordAudit(Object actor, String message, Object... args);
}
