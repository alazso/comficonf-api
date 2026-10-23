package com.comficonf.api.io;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where {@link SafeFileWriter} stores backups and how many it keeps per file. Several files, including files with
 * the same name in different directories, can share one backup directory.
 *
 * @param directory directory receiving backups; created when missing
 * @param retain number of most recent backups kept per file, 1 to 1000; older ones are deleted
 */
public record BackupPolicy(Path directory, int retain) {

    /** Number of backups kept by {@link #in(Path)}. */
    public static final int DEFAULT_RETAIN = 10;

    public BackupPolicy {
        Objects.requireNonNull(directory, "directory");
        if (retain < 1 || retain > 1000) {
            throw new IllegalArgumentException("retain must be between 1 and 1000");
        }
    }

    /** Keeps the {@link #DEFAULT_RETAIN} most recent backups per file in {@code directory}. */
    public static BackupPolicy in(Path directory) {
        return new BackupPolicy(directory, DEFAULT_RETAIN);
    }
}
