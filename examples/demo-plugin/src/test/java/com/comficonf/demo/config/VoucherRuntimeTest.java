package com.comficonf.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoucherRuntimeTest {

    @TempDir
    Path directory;

    private static String defaultConfig() throws Exception {
        try (InputStream in = VoucherRuntimeTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void reloadsUnderTheUpdateLockAndKeepsTheConfigurationOnErrors() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = defaultConfig();
        Files.writeString(file, original);
        VoucherRuntime runtime = new VoucherRuntime(VoucherConfigLoader.read(file));
        Files.writeString(file, original.replace("max-redemptions-per-day: 5", "max-redemptions-per-day: 7"));

        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<?> reload;
            runtime.updateLock().lock();
            try {
                reload = other.submit(() -> {
                    runtime.reload(file);
                    return null;
                });
                Thread.sleep(300);
                assertThat(reload).isNotDone();
                assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(5);
            } finally {
                runtime.updateLock().unlock();
            }
            reload.get(5, TimeUnit.SECONDS);
            assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(7);
        } finally {
            other.shutdownNow();
        }

        Files.writeString(file, "settings: [unclosed\n");
        assertThatThrownBy(() -> runtime.reload(file)).isInstanceOf(InvalidConfigException.class);
        assertThat(runtime.config().settings().maxRedemptionsPerDay()).isEqualTo(7);
        assertThat(runtime.updateLock().isLocked()).isFalse();
    }
}
