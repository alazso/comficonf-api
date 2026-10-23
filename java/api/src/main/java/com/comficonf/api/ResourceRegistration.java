package com.comficonf.api;

import org.bukkit.plugin.Plugin;

/** Handle of a registered resource. Thread-safe. */
public interface ResourceRegistration extends AutoCloseable {

    /** Returns the registered resource id. */
    ResourceId resourceId();

    /** Returns the plugin that owns the registration. */
    Plugin owner();

    /** Returns whether the registration is still active. */
    boolean isActive();

    /**
     * Tells the bridge that the resource's schema or values changed outside ComfiConf, for example after an
     * in-game reload command. Open editors are informed that newer values exist. Does nothing when the
     * registration is no longer active.
     */
    void notifyChanged();

    /** Unregisters the resource. Calling it more than once has no further effect. */
    void unregister();

    /** Same as {@link #unregister()}. */
    @Override
    default void close() {
        unregister();
    }
}
