package com.comficonf.demo.integration;

import com.comficonf.api.ApplyContext;
import com.comficonf.api.ApplyResult;
import com.comficonf.api.ConfigPath;
import com.comficonf.api.ConfigProvider;
import com.comficonf.api.ConfigSnapshot;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.api.io.BackupPolicy;
import com.comficonf.api.io.RevisionConflictException;
import com.comficonf.api.io.Revisions;
import com.comficonf.api.io.SafeFileWriter;
import com.comficonf.api.schema.ConfigSchema;
import com.comficonf.demo.config.InvalidConfigException;
import com.comficonf.demo.config.VoucherConfig;
import com.comficonf.demo.config.VoucherConfigLoader;
import com.comficonf.demo.config.VoucherRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Exposes {@code config.yml} to ComfiConf. Callbacks run on a bridge worker thread
 * ({@link com.comficonf.api.CallbackThread#WORKER}); only the admin notification hops to the main thread.
 */
public final class VoucherConfigProvider implements ConfigProvider {

    /** Builds the runtime configuration from a saved document. */
    @FunctionalInterface
    interface RuntimeLoader {
        VoucherConfig load(YamlConfiguration yaml) throws InvalidConfigException;
    }

    static final String STORAGE_MODE = "settings.storage-mode";
    private static final int BACKUPS_KEPT = 10;

    private final Path configFile;
    private final BackupPolicy backups;
    private final VoucherRuntime runtime;
    private final Consumer<String> adminNotifier;
    private final Logger logger;
    private final RuntimeLoader runtimeLoader;
    private final SectionMapper mapper = VoucherSchema.mapper();

    public VoucherConfigProvider(Path configFile, Path backupDirectory, VoucherRuntime runtime,
            Consumer<String> adminNotifier, Logger logger) {
        this(configFile, backupDirectory, runtime, adminNotifier, logger, VoucherConfigLoader::load);
    }

    VoucherConfigProvider(Path configFile, Path backupDirectory, VoucherRuntime runtime,
            Consumer<String> adminNotifier, Logger logger, RuntimeLoader runtimeLoader) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.backups = new BackupPolicy(backupDirectory, BACKUPS_KEPT);
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.adminNotifier = Objects.requireNonNull(adminNotifier, "adminNotifier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.runtimeLoader = Objects.requireNonNull(runtimeLoader, "runtimeLoader");
    }

    @Override
    public ConfigSchema describe() {
        return mapper.schema();
    }

    /** Reads the file once; values and revision come from the same bytes, so external edits are detected. */
    @Override
    public ConfigSnapshot read() throws IOException, InvalidConfigException {
        byte[] content = Files.readAllBytes(configFile);
        ConfigValues values = mapper.read(VoucherConfigLoader.parse(content));
        return ConfigSnapshot.of(values, Revisions.of(content));
    }

    /** Text that config.yml cannot store unchanged, then the rules the schema cannot express. */
    @Override
    public ValidationResult validate(ConfigValues proposed) {
        ValidationResult.Builder result = ValidationResult.builder();
        if (proposed.getString("settings.prefix", "").indexOf('§') >= 0) {
            result.error("settings.prefix", "Use MiniMessage tags such as <gold> instead of § color codes.");
        }
        List<ConfigValues> vouchers = proposed.getObjectList("vouchers");
        boolean anyEnabled = false;
        for (int i = 0; i < vouchers.size(); i++) {
            ConfigValues voucher = vouchers.get(i);
            boolean enabled = voucher.getBoolean("enabled", false);
            anyEnabled |= enabled;
            if (enabled && voucher.getLong("cooldown-seconds", 0) < 60) {
                result.warning("vouchers[" + i + "].cooldown-seconds",
                        "A cooldown under 60 seconds lets players redeem this voucher almost continuously.");
            }
            if (!voucher.getString("permission", "").startsWith("vouchers.")) {
                result.warning("vouchers[" + i + "].permission",
                        "Permissions usually start with 'vouchers.' so they can be granted together.");
            }
        }
        if (!anyEnabled) {
            result.error("vouchers", "At least one voucher must be enabled.");
        }
        return mapper.validate(proposed).merge(result.build());
    }

    /**
     * Returns the paths whose value in {@code saved} differs from {@code expected}. A missing value and
     * {@code null} count as equal.
     */
    private static List<String> alteredPaths(@Nullable Object expected, @Nullable Object saved, String path) {
        List<String> altered = new ArrayList<>();
        collectAltered(expected, saved, path, altered);
        return altered;
    }

    private static void collectAltered(@Nullable Object expected, @Nullable Object saved, String path,
            List<String> altered) {
        if (Objects.equals(expected, saved)) {
            return;
        }
        if (expected instanceof Map<?, ?> left && saved instanceof Map<?, ?> right) {
            Set<Object> keys = new LinkedHashSet<>(left.keySet());
            keys.addAll(right.keySet());
            for (Object key : keys) {
                collectAltered(left.get(key), right.get(key), ConfigPath.child(path, (String) key), altered);
            }
        } else if (expected instanceof List<?> left && saved instanceof List<?> right
                && left.size() == right.size()) {
            for (int i = 0; i < left.size(); i++) {
                collectAltered(left.get(i), right.get(i), ConfigPath.index(path, i), altered);
            }
        } else {
            altered.add(path);
        }
    }

    /**
     * Writes the proposed values into the current file, keeping keys the schema does not declare, then activates
     * them. Reports {@code APPLIED} only after the new runtime configuration is in use.
     *
     * <p>Text that config.yml cannot store as text is {@code REJECTED} first. The new file content is then parsed
     * again before it is written; if it does not read back to the proposed values, nothing is written and the
     * result is {@code REJECTED}. The runtime configuration is built from that parsed content, so it matches the
     * bytes on disk.
     *
     * <p>Reading, writing and swapping happen under {@link VoucherRuntime#updateLock()}, so a concurrent
     * {@code /vouchers reload} cannot swap in an older file afterwards. The main-thread notification runs after
     * the lock is released.
     */
    @Override
    public ApplyResult apply(ApplyContext context) throws IOException, InvalidConfigException {
        String newRevision;
        runtime.updateLock().lock();
        try {
            byte[] current = Files.readAllBytes(configFile);
            String currentRevision = Revisions.of(current);
            if (!currentRevision.equals(context.baseRevision())) {
                return ApplyResult.conflict(currentRevision);
            }
            ValidationResult storable = mapper.validate(context.proposed());
            if (!storable.isValid()) {
                return ApplyResult.rejected(storable);
            }
            YamlConfiguration yaml = VoucherConfigLoader.parse(current);
            mapper.write(context.proposed(), yaml);
            byte[] updated = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
            YamlConfiguration saved = VoucherConfigLoader.parse(updated);
            List<String> altered;
            try {
                altered = alteredPaths(context.proposed().asMap(), mapper.read(saved).asMap(), "");
            } catch (IllegalStateException e) {
                logger.log(Level.WARNING, "config.yml would not read back the values from ComfiConf", e);
                altered = List.of("");
            }
            if (!altered.isEmpty()) {
                ValidationResult.Builder rejection = ValidationResult.builder();
                for (String path : altered) {
                    rejection.error(path, "config.yml cannot store this value unchanged, so nothing was saved.");
                }
                return ApplyResult.rejected(rejection.build());
            }
            try {
                newRevision = SafeFileWriter.write(configFile, updated, context.baseRevision(), backups)
                        .newRevision();
            } catch (RevisionConflictException e) {
                return ApplyResult.conflict(e.currentRevision());
            }

            VoucherConfig next;
            try {
                next = runtimeLoader.load(saved);
            } catch (InvalidConfigException | RuntimeException e) {
                logger.log(Level.WARNING, "Saved config.yml from ComfiConf, but could not activate it", e);
                return ApplyResult.savedRestartRequired(newRevision, "The values were saved, but the plugin could"
                        + " not load them (" + e.getMessage() + "). They take effect after a restart or a"
                        + " successful /vouchers reload.");
            }
            runtime.swap(next);
        } finally {
            runtime.updateLock().unlock();
        }
        logger.info("Applied a configuration change from ComfiConf requested by " + context.actor().label());
        try {
            context.runSync(() -> adminNotifier.accept("Voucher settings were updated through ComfiConf by "
                    + context.actor().label() + "."));
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not notify online administrators", e);
        }

        String proposedMode = context.proposed().getString(STORAGE_MODE);
        boolean storageChanged = !proposedMode.equals(context.current().get(STORAGE_MODE));
        if (storageChanged && !proposedMode.equals(runtime.activeStorageMode())) {
            return ApplyResult.savedRestartRequired(newRevision, "Saved. The storage mode change to '"
                    + proposedMode + "' takes effect after a restart; all other changes are active.",
                    List.of(STORAGE_MODE));
        }
        return ApplyResult.applied(newRevision);
    }
}
