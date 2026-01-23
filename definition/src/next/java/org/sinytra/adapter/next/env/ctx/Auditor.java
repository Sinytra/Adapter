package org.sinytra.adapter.next.env.ctx;

public interface Auditor {
    void recordAudit(Object transform, String message, Object... args);
}
