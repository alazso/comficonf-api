package com.comficonf.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.comficonf.api.ApplyResult;
import com.comficonf.api.ApplyStatus;
import com.comficonf.api.ConfigSnapshot;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.Severity;
import com.comficonf.api.ValidationIssue;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.io.Revisions;
import com.comficonf.demo.config.InvalidConfigException;
import com.comficonf.demo.config.VoucherConfigLoader;
import com.comficonf.demo.config.VoucherRuntime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoucherConfigProviderTest {

    @TempDir
    Path directory;

    private Path configFile;
    private Path backups;
    private VoucherRuntime runtime;
    private final List<String> notices = new CopyOnWriteArrayList<>();
    private VoucherConfigProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        configFile = directory.resolve("config.yml");
        backups = directory.resolve("backups");
        Files.write(configFile, VoucherConfigMappingTest.defaultConfig());
        runtime = new VoucherRuntime(VoucherConfigLoader.read(configFile));
        provider = new VoucherConfigProvider(configFile, backups, runtime, notices::add,
                Logger.getLogger("test.provider"));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private ConfigValues validDefault() throws Exception {
        return provider.read().values();
    }

    private List<Path> backupFiles() throws Exception {
        if (!Files.isDirectory(backups)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(backups)) {
            return files.toList();
        }
    }

    @Test
    void describesTheVoucherSchema() {
        assertThat(provider.describe().fields()).extracting(field -> field.key()).containsExactly("settings",
                "vouchers");
        assertThat(provider.describe().fieldAt("vouchers[0].cooldown-seconds")).isPresent();
    }

    @Test
    void readsValuesAndTheContentHashOfTheSameBytes() throws Exception {
        ConfigSnapshot snapshot = provider.read();
        byte[] bytes = Files.readAllBytes(configFile);
        assertThat(snapshot.revision()).isEqualTo(sha256(bytes)).isEqualTo(Revisions.of(bytes));
        assertThat(snapshot.values().getString("settings.storage-mode")).isEqualTo("yaml");

        Files.writeString(configFile, Files.readString(configFile).replace("weight: 100", "weight: 101"));
        ConfigSnapshot edited = provider.read();
        assertThat(edited.revision()).isNotEqualTo(snapshot.revision());
        assertThat(edited.values().getLong("vouchers[0].weight")).isEqualTo(101L);

        Files.writeString(configFile, Files.readString(configFile) + "\n# a trailing comment\n");
        assertThat(provider.read().revision()).isNotEqualTo(edited.revision());
    }

    @Test
    void acceptsTheDefaultConfiguration() throws Exception {
        assertThat(provider.validate(validDefault()).issues()).isEmpty();
    }

    @Test
    void requiresAtLeastOneEnabledVoucher() throws Exception {
        ConfigValues values = validDefault();
        for (int i = 0; i < values.getObjectList("vouchers").size(); i++) {
            values = values.with("vouchers[" + i + "].enabled", false);
        }
        ValidationResult result = provider.validate(values);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting(ValidationIssue::path).containsExactly("vouchers");
    }

    @Test
    void rejectsLegacyColorCodesInThePrefix() throws Exception {
        ValidationResult result = provider.validate(validDefault().with("settings.prefix", "§6[Vouchers] "));
        assertThat(result.errors()).extracting(ValidationIssue::path, ValidationIssue::message).containsExactly(
                tuple("settings.prefix", "Use MiniMessage tags such as <gold> instead of § color codes."));
    }

    @Test
    void rejectsTextThatConfigYmlCannotStoreAsText() throws Exception {
        ConfigValues values = validDefault()
                .with("settings.prefix", "\u001B[33m<gold>[Vouchers]</gold> ")
                .with("settings.welcome-message", "Hello\u0085there")
                .with("vouchers[1].display-name", "Weekly\u0092s Chest");
        ValidationResult result = provider.validate(values);
        assertThat(result.errors()).extracting(ValidationIssue::path).containsExactly("settings.prefix",
                "settings.welcome-message", "vouchers[1].display-name");
        assertThat(result.errors()).allSatisfy(issue -> assertThat(issue.message()).doesNotContain("Vouchers"));

        ConfigValues fine = validDefault().with("settings.prefix", "\uD83C\uDF81 <gold>[V]</gold>\t")
                .with("settings.welcome-message", "Line one\nLine two\r\n");
        assertThat(provider.validate(fine).errors()).isEmpty();
    }

    @Test
    void refusesToSaveTextThatConfigYmlCannotStoreAsText() throws Exception {
        byte[] before = Files.readAllBytes(configFile);
        ConfigSnapshot current = provider.read();
        for (String path : List.of("settings.prefix", "settings.welcome-message", "vouchers[2].display-name")) {
            for (String text : List.of("\u001B[33m<gold>[Vouchers]</gold> ", "Hello\u0085there", "Monthly\u0007")) {
                ConfigValues proposed = current.values().with(path, text);

                ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(),
                        proposed));

                assertThat(result.status()).as(path).isEqualTo(ApplyStatus.REJECTED);
                assertThat(result.persisted()).isFalse();
                assertThat(result.issues()).extracting(ValidationIssue::path).containsExactly(path);
            }
        }
        assertThat(Files.readAllBytes(configFile)).isEqualTo(before);
        assertThat(backupFiles()).isEmpty();
        assertThat(runtime.config().settings().prefix()).isEqualTo("<gold>[Vouchers]</gold> ");
        assertThat(notices).isEmpty();
    }

    @Test
    void refusesToSaveValuesThatWouldNotReadBackUnchanged() throws Exception {
        byte[] before = Files.readAllBytes(configFile);
        ConfigSnapshot current = provider.read();
        ConfigValues proposed = current.values().with("settings.legacy-colors", true)
                .with("settings.max-redemptions-per-day", 9L);

        ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(), proposed));

        assertThat(result.status()).isEqualTo(ApplyStatus.REJECTED);
        assertThat(result.issues()).extracting(ValidationIssue::path).containsExactly("settings.legacy-colors");
        assertThat(Files.readAllBytes(configFile)).isEqualTo(before);
        assertThat(backupFiles()).isEmpty();
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(5);
    }

    @Test
    void activatesTheContentThatWasWritten() throws Exception {
        List<String> loadedPrefixes = new CopyOnWriteArrayList<>();
        VoucherConfigProvider recording = new VoucherConfigProvider(configFile, backups, runtime, notices::add,
                Logger.getLogger("test.provider"), yaml -> {
                    loadedPrefixes.add(yaml.saveToString());
                    return VoucherConfigLoader.load(yaml);
                });
        ConfigSnapshot current = recording.read();
        ApplyResult result = recording.apply(new TestApplyContext(current.revision(), current.values(),
                current.values().with("settings.prefix", "\uD83C\uDF81 ")));

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(loadedPrefixes).containsExactly(Files.readString(configFile));
        assertThat(runtime.config().settings().prefix()).isEqualTo("\uD83C\uDF81 ");
        assertThat(VoucherConfigLoader.read(configFile).settings().prefix()).isEqualTo("\uD83C\uDF81 ");
    }

    @Test
    void warnsAboutShortCooldownsAndUnusualPermissions() throws Exception {
        ConfigValues values = validDefault()
                .with("vouchers[0].cooldown-seconds", 30L)
                .with("vouchers[1].permission", "rewards.weekly")
                .with("vouchers[5].cooldown-seconds", 0L);
        ValidationResult result = provider.validate(values);
        assertThat(result.isValid()).isTrue();
        assertThat(result.issues()).extracting(ValidationIssue::path, ValidationIssue::severity).containsExactly(
                tuple("vouchers[0].cooldown-seconds", Severity.WARNING),
                tuple("vouchers[1].permission", Severity.WARNING));
    }

    @Test
    void appliesChangesWritesABackupAndSwapsTheRuntime() throws Exception {
        ConfigSnapshot current = provider.read();
        ConfigValues proposed = current.values()
                .with("settings.max-redemptions-per-day", 9L)
                .with("settings.prefix", "<green>[V]</green> ")
                .with("vouchers[0].cooldown-seconds", 120L);
        TestApplyContext context = new TestApplyContext(current.revision(), current.values(), proposed);

        ApplyResult result = provider.apply(context);

        assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        byte[] written = Files.readAllBytes(configFile);
        assertThat(result.newRevision()).isEqualTo(Revisions.of(written));
        String text = new String(written, StandardCharsets.UTF_8);
        assertThat(text).contains("max-redemptions-per-day: 9").contains("cooldown-seconds: 120")
                .contains("config-version: 3").contains("Claim once every day.")
                .contains("# Where announcements appear");
        assertThat(backupFiles()).hasSize(1);
        assertThat(Files.readAllBytes(backupFiles().getFirst())).isEqualTo(VoucherConfigMappingTest.defaultConfig());
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(9);
        assertThat(runtime.config().settings().prefix()).isEqualTo("<green>[V]</green> ");
        assertThat(runtime.config().voucher("daily").orElseThrow().cooldownSeconds()).isEqualTo(120);
        assertThat(runtime.config().voucher("daily").orElseThrow().lore()).containsExactly("Claim once every day.");
        assertThat(context.syncCalls.get()).isEqualTo(1);
        assertThat(notices).singleElement().asString().contains("owner@example.com");
        assertThat(provider.read().revision()).isEqualTo(result.newRevision());
    }

    @Test
    void reportsAConflictWhenTheFileChangedSinceTheEditorReadIt() throws Exception {
        ConfigSnapshot current = provider.read();
        Files.writeString(configFile, Files.readString(configFile).replace("weight: 50", "weight: 55"));
        byte[] edited = Files.readAllBytes(configFile);
        TestApplyContext context = new TestApplyContext(current.revision(), current.values(),
                current.values().with("settings.max-redemptions-per-day", 9L));

        ApplyResult result = provider.apply(context);

        assertThat(result.status()).isEqualTo(ApplyStatus.CONFLICT);
        assertThat(result.currentRevision()).isEqualTo(Revisions.of(edited));
        assertThat(result.persisted()).isFalse();
        assertThat(Files.readAllBytes(configFile)).isEqualTo(edited);
        assertThat(backupFiles()).isEmpty();
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(5);
    }

    @Test
    void keepsTheActiveStorageModeUntilRestart() throws Exception {
        ConfigSnapshot current = provider.read();
        ConfigValues proposed = current.values()
                .with("settings.storage-mode", "sqlite")
                .with("settings.bonus-multiplier", 3.0);

        ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(), proposed));

        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(result.restartRequired()).containsExactly("settings.storage-mode");
        assertThat(result.persisted()).isTrue();
        assertThat(result.activated()).isFalse();
        assertThat(result.message()).contains("restart");
        assertThat(runtime.activeStorageMode()).isEqualTo("yaml");
        assertThat(runtime.pendingStorageMode()).isEqualTo("sqlite");
        assertThat(runtime.config().settings().bonusMultiplier()).isEqualTo(3.0);
        assertThat(Files.readString(configFile)).contains("storage-mode: sqlite");

        ConfigSnapshot after = provider.read();
        ApplyResult next = provider.apply(new TestApplyContext(after.revision(), after.values(),
                after.values().with("settings.bonus-multiplier", 2.5)));
        assertThat(next.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(runtime.pendingStorageMode()).isEqualTo("sqlite");

        ConfigSnapshot back = provider.read();
        ApplyResult reverted = provider.apply(new TestApplyContext(back.revision(), back.values(),
                back.values().with("settings.storage-mode", "yaml")));
        assertThat(reverted.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(runtime.pendingStorageMode()).isNull();
    }

    @Test
    void appliesUnderTheRuntimeUpdateLockAndNotifiesAfterReleasingIt() throws Exception {
        List<Boolean> notifiedUnderLock = new CopyOnWriteArrayList<>();
        VoucherConfigProvider locking = new VoucherConfigProvider(configFile, backups, runtime,
                message -> notifiedUnderLock.add(runtime.updateLock().isHeldByCurrentThread()),
                Logger.getLogger("test.provider"));
        ConfigSnapshot current = locking.read();
        byte[] before = Files.readAllBytes(configFile);
        TestApplyContext context = new TestApplyContext(current.revision(), current.values(),
                current.values().with("settings.max-redemptions-per-day", 9L));

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<ApplyResult> apply;
            runtime.updateLock().lock();
            try {
                apply = worker.submit(() -> locking.apply(context));
                Thread.sleep(300);
                assertThat(apply).isNotDone();
                assertThat(Files.readAllBytes(configFile)).isEqualTo(before);
                assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(5);
            } finally {
                runtime.updateLock().unlock();
            }
            assertThat(apply.get(5, TimeUnit.SECONDS).status()).isEqualTo(ApplyStatus.APPLIED);
        } finally {
            worker.shutdownNow();
        }
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(9);
        assertThat(notifiedUnderLock).containsExactly(false);
        assertThat(runtime.updateLock().isLocked()).isFalse();
    }

    @Test
    void reportsAStorageModeChangeFromAnInvalidOrMissingStoredValue() throws Exception {
        String original = Files.readString(configFile);
        // Only the text of the line is replaced: config.yml has CRLF line breaks in a Windows checkout.
        for (String stored : List.of("  storage-mode: 5", "")) {
            Files.writeString(configFile, original.replace("  storage-mode: yaml", stored));
            ConfigSnapshot current = provider.read();
            assertThat(current.values().get("settings.storage-mode")).isEqualTo(stored.isEmpty() ? null : "5");

            ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(),
                    current.values().with("settings.storage-mode", "sqlite")));

            assertThat(result.status()).as(stored).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
            assertThat(result.restartRequired()).containsExactly("settings.storage-mode");
            assertThat(Files.readString(configFile)).contains("storage-mode: sqlite");
            assertThat(runtime.activeStorageMode()).isEqualTo("yaml");
            assertThat(runtime.pendingStorageMode()).isEqualTo("sqlite");
        }
    }

    @Test
    void reportsSavedRestartRequiredWhenTheLiveReloadFails() throws Exception {
        VoucherConfigProvider failing = new VoucherConfigProvider(configFile, backups, runtime, notices::add,
                Logger.getLogger("test.provider"), yaml -> {
                    throw new InvalidConfigException("reward table is locked");
                });
        ConfigSnapshot current = failing.read();
        ApplyResult result = failing.apply(new TestApplyContext(current.revision(), current.values(),
                current.values().with("settings.max-redemptions-per-day", 9L)));
        assertThat(result.status()).isEqualTo(ApplyStatus.SAVED_RESTART_REQUIRED);
        assertThat(result.message()).contains("reward table is locked");
        assertThat(result.restartRequired()).isEmpty();
        assertThat(Files.readString(configFile)).contains("max-redemptions-per-day: 9");
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(5);
        assertThat(notices).isEmpty();
    }

    @Test
    void keepsTenBackups() throws Exception {
        for (int i = 0; i < 12; i++) {
            ConfigSnapshot current = provider.read();
            ApplyResult result = provider.apply(new TestApplyContext(current.revision(), current.values(),
                    current.values().with("settings.max-redemptions-per-day", (long) (10 + i))));
            assertThat(result.status()).isEqualTo(ApplyStatus.APPLIED);
        }
        assertThat(backupFiles()).hasSize(10);
    }
}
