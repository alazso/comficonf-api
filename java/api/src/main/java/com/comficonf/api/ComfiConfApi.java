package com.comficonf.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point of the ComfiConf runtime, implemented by the ComfiConf bridge plugin and registered with the
 * Bukkit {@link org.bukkit.plugin.ServicesManager}.
 *
 * <p>Classes of this package are only loadable while the bridge is installed. Plugins that treat ComfiConf
 * as optional must check {@code getServer().getPluginManager().isPluginEnabled("ComfiConf")} before touching
 * any class of this API, and keep all API usage inside classes that are only loaded after that check.
 *
 * <p>All methods are thread-safe.
 */
public interface ComfiConfApi {

    /** Version of this API contract. Incremented when methods are added. */
    int API_VERSION = 1;

    /** Plugin name of the bridge, as used in {@code depend}/{@code softdepend} lists. */
    String PLUGIN_NAME = "ComfiConf";

    /**
     * Returns the API version implemented by the running bridge. A plugin compiled against a newer API than
     * the installed bridge can compare this against {@link #API_VERSION} before calling newer methods.
     */
    int apiVersion();

    /** Returns the version string of the running bridge plugin. */
    String bridgeVersion();

    /**
     * Registers a configuration resource owned by {@code owner}.
     *
     * <p>The resource stays registered until {@link ResourceRegistration#unregister()} is called,
     * {@link #unregisterAll(Plugin)} is called for the owner, or the owner is disabled.
     *
     * @throws IllegalArgumentException if another resource with the same id is registered
     * @throws IllegalStateException if {@code owner} is not enabled
     */
    ResourceRegistration register(Plugin owner, ConfigResource resource);

    /** Unregisters every resource owned by {@code owner}. Does nothing if it owns none. */
    void unregisterAll(Plugin owner);

    /** Returns the active registrations owned by {@code owner}. */
    List<ResourceRegistration> registrations(Plugin owner);

    /** Returns the current state of the bridge's connection to the ComfiConf backend. */
    ConnectionState connectionState();

    /**
     * Looks up the running API implementation.
     *
     * @return the implementation, or empty when the bridge is not enabled
     */
    static Optional<ComfiConfApi> find() {
        RegisteredServiceProvider<ComfiConfApi> registration =
                Bukkit.getServicesManager().getRegistration(ComfiConfApi.class);
        return registration == null ? Optional.empty() : Optional.of(registration.getProvider());
    }

    /**
     * Returns the running API implementation.
     *
     * @throws IllegalStateException when the bridge is not enabled
     */
    static ComfiConfApi get() {
        return find().orElseThrow(() -> new IllegalStateException(
                "The ComfiConf bridge is not installed or not enabled"));
    }
}
