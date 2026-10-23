package com.comficonf.demo.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Holds the active configuration and swaps it atomically on reload. Readers always see one complete
 * configuration. The storage mode is fixed when the plugin starts, so a changed {@code storage-mode} only takes
 * effect after a restart and is reported as pending until then.
 *
 * <p>Code that reads or writes the configuration file and then swaps in the configuration built from it holds
 * {@link #updateLock()} for that whole sequence, so the active configuration always belongs to the file content
 * that was processed last.
 *
 * <p>Thread-safe.
 */
public final class VoucherRuntime {

    private final AtomicReference<VoucherConfig> config;
    private final String activeStorageMode;
    private final ReentrantLock updateLock = new ReentrantLock();

    public VoucherRuntime(VoucherConfig initial) {
        this.config = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.activeStorageMode = initial.settings().storageMode();
    }

    public VoucherConfig config() {
        return config.get();
    }

    /** Replaces the active configuration. */
    public void swap(VoucherConfig next) {
        config.set(Objects.requireNonNull(next, "next"));
    }

    /**
     * Lock serializing file reads and writes with the {@link #swap} that follows them. Never wait for the server
     * main thread while holding it, because {@link #reload} takes it on the main thread.
     */
    public ReentrantLock updateLock() {
        return updateLock;
    }

    /**
     * Re-reads {@code file} under {@link #updateLock()} and swaps in its configuration. Keeps the active
     * configuration when the file cannot be read or is invalid.
     */
    public void reload(Path file) throws IOException, InvalidConfigException {
        updateLock.lock();
        try {
            swap(VoucherConfigLoader.read(file));
        } finally {
            updateLock.unlock();
        }
    }

    /** Storage mode in use since the plugin started. */
    public String activeStorageMode() {
        return activeStorageMode;
    }

    /** Storage mode configured in the file when it differs from the active one, or null. */
    public @Nullable String pendingStorageMode() {
        String configured = config.get().settings().storageMode();
        return configured.equals(activeStorageMode) ? null : configured;
    }
}
