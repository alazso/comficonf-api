package com.comficonf.demo;

/**
 * The plugin's view of its optional ComfiConf integration. Declared in an API-free package so that the main class
 * can hold an integration without referencing any {@code com.comficonf.api} type.
 */
public interface DemoIntegration {

    /** Id of the registered configuration resource. */
    String resourceId();

    /** Whether the resource is still registered with the bridge. */
    boolean isRegistered();

    /** Tells the bridge that the configuration changed outside ComfiConf. */
    void notifyChanged();

    /** Unregisters the resource. */
    void close();
}
