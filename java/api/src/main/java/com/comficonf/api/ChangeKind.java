package com.comficonf.api;

/** Kind of a {@link FieldChange}. */
public enum ChangeKind {
    /** A value changed. */
    MODIFIED("modified"),
    /** An object list item was added. */
    ADDED("added"),
    /** An object list item was removed. */
    REMOVED("removed"),
    /** The items of an ordered object list changed order. */
    REORDERED("reordered");

    private final String protocolName;

    ChangeKind(String protocolName) {
        this.protocolName = protocolName;
    }

    /** Name used in the wire protocol. */
    public String protocolName() {
        return protocolName;
    }
}
