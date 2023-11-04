package dev.su5ed.sinytra.adapter.gradle.util;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

public class TraceCallback {
    private final Logger logger;
    private final ClassNode cleanNode;

    private boolean loggedHeader = false;

    public TraceCallback(Logger logger, ClassNode cleanNode) {
        this.logger = logger;
        this.cleanNode = cleanNode;
    }

    public void logHeader() {
        if (!this.loggedHeader) {
            this.logger.info("Class {}", this.cleanNode.name);
            this.loggedHeader = true;
        }
    }

    public void space() {
        if (this.loggedHeader) {
            this.logger.info("");
        }
    }

    public void reset() {
        this.loggedHeader = false;
    }
}
