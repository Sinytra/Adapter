package org.sinytra.adapter.patch;

public enum TxPhase {
    /**
     * Right after parsing, before preProcess is called
     */
    EARLY,
    /**
     * After preProcess is called
     */
    LOADED,
    /**
     * After clean config is validated
     */
    VALIDATED
}
