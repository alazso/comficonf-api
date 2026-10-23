package com.comficonf.api.schema;

/** How a change to a field takes effect. */
public enum ApplyMode {
    /** The plugin activates the change at runtime when it is applied. */
    LIVE("live"),
    /** The change is persisted immediately but takes effect only after a server restart. */
    RESTART("restart");

    private final String protocolName;

    ApplyMode(String protocolName) {
        this.protocolName = protocolName;
    }

    /** Name used in the wire protocol. */
    public String protocolName() {
        return protocolName;
    }
}
