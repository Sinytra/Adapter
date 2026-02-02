package org.sinytra.adapter.env.ctx;

public interface Auditor {
    void recordAudit(Object actor, String message, Object... args);
}
