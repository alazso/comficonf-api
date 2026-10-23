package com.comficonf.api;

/** Severity of a {@link ValidationIssue}. */
public enum Severity {
    /** The value is invalid. Any error prevents the change from being applied. */
    ERROR("error"),
    /** The value is accepted but worth reviewing. Warnings never block a change. */
    WARNING("warning");

    private final String protocolName;

    Severity(String protocolName) {
        this.protocolName = protocolName;
    }

    /** Name used in the wire protocol. */
    public String protocolName() {
        return protocolName;
    }
}
