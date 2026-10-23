package com.comficonf.demo;

import com.comficonf.demo.config.InvalidConfigException;
import com.comficonf.demo.config.VoucherConfig;
import com.comficonf.demo.config.VoucherConfigLoader;
import com.comficonf.demo.config.VoucherRuntime;
import com.comficonf.demo.integration.ComfiConfIntegration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * ComfiConfDemo: a voucher plugin that works on its own and exposes its configuration to ComfiConf when the
 * bridge is installed.
 *
 * <p>This class references no {@code com.comficonf.api} type, so it loads without the bridge. The integration is
 * only touched after {@code isPluginEnabled("ComfiConf")} returned true.
 */
public final class ComfiConfDemoPlugin extends JavaPlugin implements Listener {

    static final String ADMIN_PERMISSION = "comficonf.demo.admin";
    private static final String BRIDGE = "ComfiConf";

    private @Nullable VoucherRuntime runtime;
    private @Nullable RedemptionService redemptions;
    private @Nullable DemoIntegration integration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        VoucherConfig config;
        try {
            config = VoucherConfigLoader.read(configFile());
        } catch (IOException | InvalidConfigException e) {
            getLogger().severe("config.yml is invalid (" + e.getMessage() + "); using the bundled defaults until it"
                    + " is fixed and /vouchers reload is run");
            config = bundledDefaults();
        }
        VoucherRuntime runtime = new VoucherRuntime(config);
        this.runtime = runtime;
        this.redemptions = new RedemptionService(Clock.systemUTC());

        PluginCommand command = getCommand("vouchers");
        if (command != null) {
            VouchersCommand handler = new VouchersCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().isPluginEnabled(BRIDGE)) {
            try {
                integration = ComfiConfIntegration.register(this, runtime, configFile(), backupDirectory(),
                        this::notifyAdmins);
                getLogger().info("Registered " + integration.resourceId() + " with ComfiConf");
            } catch (LinkageError e) {
                Plugin bridge = getServer().getPluginManager().getPlugin(BRIDGE);
                getLogger().warning(incompatibleBridgeMessage(
                        bridge == null ? "?" : bridge.getPluginMeta().getVersion(), e));
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "Could not register with ComfiConf; continuing without it", e);
            }
        } else {
            getLogger().info("ComfiConf is not installed; vouchers are configured through config.yml only");
        }
        getLogger().info("Loaded " + config.vouchers().size() + " vouchers (storage mode "
                + runtime.activeStorageMode() + ")");
    }

    /**
     * Warning for a ComfiConf plugin whose classes do not match the API this plugin was compiled against, such as a
     * bridge older than that API.
     */
    static String incompatibleBridgeMessage(String bridgeVersion, LinkageError error) {
        String missing = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : "missing " + error.getMessage().replace('/', '.');
        return "The installed ComfiConf " + bridgeVersion + " does not include the ComfiConf API this plugin needs ("
                + missing + "). Update ComfiConf to the version this plugin was built for; vouchers are configured"
                + " through config.yml only until then.";
    }

    @Override
    public void onDisable() {
        DemoIntegration current = integration;
        if (current != null) {
            current.close();
            integration = null;
        }
    }

    /** Sends the welcome message of the active configuration. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String message = runtime().config().settings().welcomeMessage();
        if (!message.isBlank()) {
            event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(message));
        }
    }

    /**
     * Re-reads config.yml and swaps the runtime configuration. Keeps the previous configuration when the file is
     * invalid.
     *
     * @return null on success, or the problem that prevented the reload
     */
    @Nullable String reload() {
        try {
            runtime().reload(configFile());
        } catch (IOException | InvalidConfigException e) {
            return e.getMessage();
        }
        DemoIntegration current = integration;
        if (current != null) {
            current.notifyChanged();
        }
        return null;
    }

    VoucherRuntime runtime() {
        VoucherRuntime current = runtime;
        if (current == null) {
            throw new IllegalStateException("ComfiConfDemo is not enabled");
        }
        return current;
    }

    RedemptionService redemptions() {
        RedemptionService current = redemptions;
        if (current == null) {
            throw new IllegalStateException("ComfiConfDemo is not enabled");
        }
        return current;
    }

    @Nullable DemoIntegration integration() {
        return integration;
    }

    boolean bridgeInstalled() {
        return getServer().getPluginManager().isPluginEnabled(BRIDGE);
    }

    /** Tells online administrators about a configuration change. Main thread only. */
    void notifyAdmins(String message) {
        Component text = MiniMessage.miniMessage().deserialize(runtime().config().settings().prefix())
                .append(Component.text(message));
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(text);
            }
        }
    }

    private Path configFile() {
        return getDataFolder().toPath().resolve("config.yml");
    }

    private Path backupDirectory() {
        return getDataFolder().toPath().resolve("backups");
    }

    private VoucherConfig bundledDefaults() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("The plugin JAR does not contain config.yml");
            }
            return VoucherConfigLoader.load(VoucherConfigLoader.parse(in.readAllBytes()));
        } catch (IOException | InvalidConfigException e) {
            throw new IllegalStateException("The bundled config.yml is invalid", e);
        }
    }
}
