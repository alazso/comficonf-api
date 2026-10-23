package com.comficonf.demo.integration;

import com.comficonf.api.CallbackThread;
import com.comficonf.api.ComfiConfApi;
import com.comficonf.api.ConfigResource;
import com.comficonf.api.ResourceRegistration;
import com.comficonf.demo.DemoIntegration;
import com.comficonf.demo.config.VoucherRuntime;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the voucher configuration with ComfiConf. This is the only class the plugin loads that refers to the
 * ComfiConf API; it is used only after the bridge was found enabled.
 */
public final class ComfiConfIntegration implements DemoIntegration {

    public static final String RESOURCE_ID = "comficonf-demo:vouchers";

    private final ResourceRegistration registration;

    private ComfiConfIntegration(ResourceRegistration registration) {
        this.registration = registration;
    }

    /**
     * Registers the resource.
     *
     * @param adminNotifier sends a message to online administrators; called on the main thread
     * @throws IllegalStateException if the bridge is not enabled
     */
    public static DemoIntegration register(JavaPlugin plugin, VoucherRuntime runtime, Path configFile,
            Path backupDirectory, Consumer<String> adminNotifier) {
        ConfigResource resource = ConfigResource.builder(RESOURCE_ID)
                .displayName("Vouchers")
                .description("Voucher rewards, cooldowns and announcements of ComfiConfDemo.")
                .provider(new VoucherConfigProvider(configFile, backupDirectory, runtime, adminNotifier,
                        plugin.getLogger()))
                .callbackThread(CallbackThread.WORKER)
                .build();
        return new ComfiConfIntegration(ComfiConfApi.get().register(plugin, resource));
    }

    @Override
    public String resourceId() {
        return RESOURCE_ID;
    }

    @Override
    public boolean isRegistered() {
        return registration.isActive();
    }

    @Override
    public void notifyChanged() {
        registration.notifyChanged();
    }

    @Override
    public void close() {
        registration.unregister();
    }
}
