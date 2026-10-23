package com.comficonf.api;

/** State of the bridge's connection to the ComfiConf backend. */
public enum ConnectionState {
    /** Not connected; a reconnect may be scheduled. */
    DISCONNECTED,
    /** A connection attempt is in progress. */
    CONNECTING,
    /** Connected and authenticated, but this installation is not paired with an account. */
    UNPAIRED,
    /** Connected, authenticated and paired. Remote editing is available. */
    CONNECTED
}
