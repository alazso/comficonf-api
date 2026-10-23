package com.comficonf.api.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Replaces a file's content safely: checks the expected revision, backs up the previous content, writes to a
 * temporary file in the same directory and moves it over the target.
 *
 * <p>The move is atomic where the file system supports it, so readers see either the old or the new content.
 * When atomic moves are unsupported the file is replaced with a regular move. If any step fails before the
 * move, the target is unchanged, and the temporary file and the backup taken by that call are deleted. Older
 * backups beyond {@link BackupPolicy#retain()} are deleted only after the move succeeded.
 *
 * <p>A target that is a symbolic link is resolved first: the real file is replaced and the link keeps pointing
 * to it. The new file gets the POSIX permissions of the file it replaces, and its owner and group where the
 * process may set them. A hard link of the target is not updated, because the move puts a new file under the
 * target's name; the other names keep the previous content.
 *
 * <p>Writes to the same path from this JVM are serialized with a per-path lock, which plugins can also take
 * with {@link #lockFor(Path)} around their own writes. Edits by other processes are detected through the
 * revision check, which runs immediately before the backup and the move.
 *
 * <p>Backups are plain copies named {@code <file name>.<path id>.<yyyyMMdd-HHmmss-SSS>.bak}, with the time in
 * UTC, where the path id is the first 12 hex characters of the SHA-256 of the file's absolute real path. Files with the same name in
 * different directories can therefore share one backup directory. A backup is restored by copying it over the
 * target, or with {@link #restoreLatestBackup}. No backup is created when the newest backup of the file already
 * has the current content.
 */
public final class SafeFileWriter {

    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    /** Part of a backup name after the prefix: the timestamp, an optional counter and {@code .bak}. */
    private static final Pattern BACKUP_SUFFIX = Pattern.compile("(\\d{8}-\\d{6}-\\d{3})(?:-(\\d{1,9}))?\\.bak");
    private static final int PATH_ID_LENGTH = 12;

    /** Moves the written temporary file over the target. */
    @FunctionalInterface
    interface Mover {
        void move(Path source, Path target) throws IOException;
    }

    private static final Mover REPLACING_MOVE = (source, target) -> {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    };

    private SafeFileWriter() {
    }

    /**
     * Outcome of a successful write.
     *
     * @param newRevision revision of the written content
     * @param backup backup of the previous content, or null when the file did not exist
     */
    public record WriteResult(String newRevision, @Nullable Path backup) {
    }

    /**
     * Returns the lock serializing writes to {@code target} within this JVM. The lock is keyed by the absolute,
     * normalized path as given, without resolving links.
     */
    public static ReentrantLock lockFor(Path target) {
        return LOCKS.computeIfAbsent(target.toAbsolutePath().normalize(), path -> new ReentrantLock());
    }

    /**
     * Replaces the content of {@code target}.
     *
     * @param target file to replace; its parent directory must exist
     * @param content new content
     * @param expectedRevision revision the file must currently have, as returned by
     *     {@link Revisions#ofFile(Path)}, or null to skip the check
     * @param backups where to store the backup of the previous content
     * @throws RevisionConflictException if the file's revision differs from {@code expectedRevision}; nothing
     *     was written
     * @throws IOException if reading, backing up or writing fails; the target is unchanged unless the final
     *     move itself failed partway, which atomic moves rule out
     */
    public static WriteResult write(Path target, byte[] content, @Nullable String expectedRevision,
            BackupPolicy backups) throws IOException, RevisionConflictException {
        return write(target, content, expectedRevision, backups, REPLACING_MOVE);
    }

    static WriteResult write(Path target, byte[] content, @Nullable String expectedRevision, BackupPolicy backups,
            Mover mover) throws IOException, RevisionConflictException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(backups, "backups");
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            Path file = realPath(target);
            Path directory = file.getParent();
            if (directory == null || !Files.isDirectory(directory)) {
                throw new IOException("Parent directory of " + file + " does not exist");
            }
            String currentRevision = Revisions.ofFile(file);
            if (expectedRevision != null && !expectedRevision.equals(currentRevision)) {
                throw new RevisionConflictException(expectedRevision, currentRevision);
            }
            boolean exists = !currentRevision.equals(Revisions.ABSENT);
            Path backup = null;
            boolean backupCreated = false;
            if (exists) {
                backup = newestBackupWith(file, backups, currentRevision);
                if (backup == null) {
                    backup = createBackup(file, backups);
                    backupCreated = true;
                }
            }
            Path temp = null;
            try {
                temp = Files.createTempFile(directory, "." + file.getFileName() + ".", ".tmp");
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(content);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                if (exists) {
                    copyOwnership(file, temp);
                }
                mover.move(temp, file);
            } catch (Throwable failure) {
                if (temp != null) {
                    deleteAfterFailure(temp, failure);
                }
                if (backupCreated) {
                    deleteAfterFailure(backup, failure);
                }
                throw failure;
            }
            try {
                prune(file, backups);
            } catch (IOException e) {
                // The new content is in place; a failed prune only leaves additional old backups.
            }
            return new WriteResult(Revisions.of(content), backup);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Lists the backups of {@code target}, newest first.
     */
    public static List<Path> listBackups(Path target, BackupPolicy backups) throws IOException {
        return listBackupsOf(realPath(target), backups);
    }

    /** A backup file with its timestamp and the counter added when several backups share a timestamp. */
    private record BackupName(Path path, String stamp, int attempt) {
    }

    /**
     * Copies the newest backup of {@code target} over it, backing up the content being replaced first.
     *
     * @return the restored backup, or empty when there is none
     */
    public static Optional<Path> restoreLatestBackup(Path target, BackupPolicy backups) throws IOException {
        List<Path> available = listBackups(target, backups);
        if (available.isEmpty()) {
            return Optional.empty();
        }
        Path latest = available.getFirst();
        byte[] content = Files.readAllBytes(latest);
        try {
            write(target, content, null, backups);
        } catch (RevisionConflictException e) {
            throw new IllegalStateException("Unreachable: no revision was expected", e);
        }
        return Optional.of(latest);
    }

    /** Returns the start of every backup name of {@code target}, up to the timestamp. */
    static String backupPrefix(Path target) throws IOException {
        return prefixOf(realPath(target));
    }

    private static String prefixOf(Path file) {
        String pathId = Revisions.of(file.toString().getBytes(StandardCharsets.UTF_8)).substring(0, PATH_ID_LENGTH);
        return safeName(file.getFileName().toString()) + "." + pathId + ".";
    }

    /**
     * Returns the absolute path of {@code target} with symbolic links resolved: the real path of an existing
     * file, or the real path of an existing parent directory joined with the file name.
     */
    private static Path realPath(Path target) throws IOException {
        Path file = target.toAbsolutePath().normalize();
        if (Files.exists(file)) {
            try {
                return file.toRealPath();
            } catch (NoSuchFileException e) {
                // Deleted since the check; resolved like a file that does not exist.
            }
        }
        Path parent = file.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            return parent.toRealPath().resolve(file.getFileName());
        }
        return file;
    }

    private static List<Path> listBackupsOf(Path file, BackupPolicy backups) throws IOException {
        String prefix = prefixOf(file);
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(backups.directory())) {
            return result;
        }
        List<BackupName> found = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backups.directory())) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                Matcher matcher = BACKUP_SUFFIX.matcher(name.substring(prefix.length()));
                if (matcher.matches() && Files.isRegularFile(entry)) {
                    int attempt = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                    found.add(new BackupName(entry, matcher.group(1), attempt));
                }
            }
        }
        found.sort(Comparator.comparing(BackupName::stamp).thenComparingInt(BackupName::attempt).reversed());
        for (BackupName backup : found) {
            result.add(backup.path());
        }
        return result;
    }

    private static @Nullable Path newestBackupWith(Path file, BackupPolicy backups, String revision)
            throws IOException {
        List<Path> existing = listBackupsOf(file, backups);
        if (!existing.isEmpty() && Revisions.ofFile(existing.getFirst()).equals(revision)) {
            return existing.getFirst();
        }
        return null;
    }

    private static Path createBackup(Path file, BackupPolicy backups) throws IOException {
        Files.createDirectories(backups.directory());
        String base = prefixOf(file) + LocalDateTime.now(ZoneOffset.UTC).format(BACKUP_TIME);
        Path backup = backups.directory().resolve(base + ".bak");
        for (int attempt = 1; Files.exists(backup); attempt++) {
            backup = backups.directory().resolve(base + "-" + attempt + ".bak");
        }
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void prune(Path file, BackupPolicy backups) throws IOException {
        List<Path> existing = listBackupsOf(file, backups);
        for (int i = backups.retain(); i < existing.size(); i++) {
            Files.deleteIfExists(existing.get(i));
        }
    }

    /** Gives {@code copy} the POSIX permissions, owner and group of {@code original} where possible. */
    private static void copyOwnership(Path original, Path copy) throws IOException {
        PosixFileAttributeView source = Files.getFileAttributeView(original, PosixFileAttributeView.class);
        PosixFileAttributeView target = Files.getFileAttributeView(copy, PosixFileAttributeView.class);
        if (source == null || target == null) {
            return;
        }
        PosixFileAttributes attributes = source.readAttributes();
        target.setPermissions(attributes.permissions());
        try {
            target.setOwner(attributes.owner());
        } catch (IOException | UnsupportedOperationException e) {
            // Changing the owner needs privileges the process may not have; the writing user stays the owner.
        }
        try {
            target.setGroup(attributes.group());
        } catch (IOException | UnsupportedOperationException e) {
            // Changing the group needs membership in that group; the writing user's group stays.
        }
    }

    private static void deleteAfterFailure(Path path, Throwable failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
