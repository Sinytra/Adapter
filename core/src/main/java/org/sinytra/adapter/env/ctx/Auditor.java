package org.sinytra.adapter.env.ctx;

public interface Auditor {
    void recordAudit(Object transform, String message, Object... args);
}
