package com.comficonf.api;

/** Outcome of an apply operation. */
public enum ApplyStatus {
    /** The values were persisted and are active at runtime. */
    APPLIED,
    /**
     * The values were persisted, but some or all changes take effect only after a server restart, either
     * because the changed fields cannot be reloaded or because the runtime reload failed.
     */
    SAVED_RESTART_REQUIRED,
    /** Validation failed. Nothing was persisted. */
    REJECTED,
    /** The stored values changed since the editor read them. Nothing was persisted. */
    CONFLICT,
    /** The operation failed. Nothing was persisted unless the result states otherwise. */
    FAILED
}
