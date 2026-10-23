package com.comficonf.api.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeFileWriterTest {

    @TempDir
    Path directory;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void replacesTheFileAndBacksUpThePreviousContent() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("a: 1\n"));
        BackupPolicy policy = BackupPolicy.in(directory.resolve("backups"));
        SafeFileWriter.WriteResult result = SafeFileWriter.write(target, bytes("a: 2\n"),
                Revisions.ofFile(target), policy);
        assertThat(Files.readString(target)).isEqualTo("a: 2\n");
        assertThat(result.newRevision()).isEqualTo(Revisions.of(bytes("a: 2\n")));
        assertThat(result.backup()).isNotNull();
        assertThat(Files.readString(result.backup())).isEqualTo("a: 1\n");
        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactlyInAnyOrder("config.yml",
                    "backups");
        }
    }

    @Test
    void refusesToWriteWhenTheRevisionChanged() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("a: 1\n"));
        String expected = Revisions.ofFile(target);
        Files.write(target, bytes("a: 9\n"));
        BackupPolicy policy = BackupPolicy.in(directory.resolve("backups"));
        assertThatThrownBy(() -> SafeFileWriter.write(target, bytes("a: 2\n"), expected, policy))
                .isInstanceOfSatisfying(RevisionConflictException.class, e -> {
                    assertThat(e.expectedRevision()).isEqualTo(expected);
                    assertThat(e.currentRevision()).isEqualTo(Revisions.of(bytes("a: 9\n")));
                });
        assertThat(Files.readString(target)).isEqualTo("a: 9\n");
        assertThat(Files.exists(directory.resolve("backups"))).isFalse();
    }

    @Test
    void createsMissingFilesWithoutABackup() throws Exception {
        Path target = directory.resolve("new.yml");
        assertThat(Revisions.ofFile(target)).isEqualTo(Revisions.ABSENT);
        SafeFileWriter.WriteResult result = SafeFileWriter.write(target, bytes("x"), Revisions.ABSENT,
                BackupPolicy.in(directory.resolve("backups")));
        assertThat(result.backup()).isNull();
        assertThat(Files.readString(target)).isEqualTo("x");
    }

    @Test
    void keepsTheNewestBackupsEvenWithinOneMillisecond() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("v0"));
        BackupPolicy policy = new BackupPolicy(directory.resolve("backups"), 3);
        for (int i = 1; i <= 8; i++) {
            SafeFileWriter.write(target, bytes("v" + i), null, policy);
        }
        List<Path> backups = SafeFileWriter.listBackups(target, policy);
        assertThat(backups).hasSize(3);
        assertThat(backups.stream().map(SafeFileWriterTest::read).toList()).containsExactly("v7", "v6", "v5");
    }

    @Test
    void ordersBackupsOfTheSameMillisecondByCreation() throws Exception {
        Path target = directory.resolve("config.yml");
        Path backupDirectory = Files.createDirectories(directory.resolve("backups"));
        BackupPolicy policy = BackupPolicy.in(backupDirectory);
        String prefix = SafeFileWriter.backupPrefix(target);
        assertThat(prefix).matches("config\\.yml\\.[0-9a-f]{12}\\.");
        Files.writeString(backupDirectory.resolve(prefix + "20261019-101010-122.bak"), "oldest");
        Files.writeString(backupDirectory.resolve(prefix + "20261019-101010-123.bak"), "first");
        Files.writeString(backupDirectory.resolve(prefix + "20261019-101010-123-1.bak"), "second");
        Files.writeString(backupDirectory.resolve(prefix + "20261019-101010-123-2.bak"), "third");
        Files.writeString(backupDirectory.resolve(prefix + "20261019-101010-123-10.bak"), "eleventh");
        Files.writeString(backupDirectory.resolve("other.yml.20261019-101010-124.bak"), "other");
        Files.writeString(backupDirectory.resolve(SafeFileWriter.backupPrefix(directory.resolve("config.yml.old"))
                + "20261019-101010-125.bak"), "sibling file");
        Files.writeString(backupDirectory.resolve(SafeFileWriter.backupPrefix(directory.resolve("x").resolve(
                "config.yml")) + "20261019-101010-126.bak"), "same name elsewhere");
        Files.writeString(backupDirectory.resolve("config.yml.20261019-101010-127.bak"), "no path id");
        Files.writeString(backupDirectory.resolve(prefix + "notes.bak"), "not a backup");
        assertThat(SafeFileWriter.listBackups(target, policy).stream().map(SafeFileWriterTest::read).toList())
                .containsExactly("eleventh", "third", "second", "first", "oldest");
    }

    @Test
    void keepsBackupsOfSameNamedFilesInOneDirectoryApart() throws Exception {
        Path a = Files.createDirectories(directory.resolve("arenas").resolve("a")).resolve("settings.yml");
        Path b = Files.createDirectories(directory.resolve("arenas").resolve("b")).resolve("settings.yml");
        Files.write(a, bytes("name: A0"));
        Files.write(b, bytes("name: B0"));
        BackupPolicy policy = new BackupPolicy(directory.resolve("backups"), 3);
        SafeFileWriter.write(a, bytes("name: A1"), null, policy);
        for (int i = 1; i <= 3; i++) {
            SafeFileWriter.write(b, bytes("name: B" + i), null, policy);
        }

        assertThat(SafeFileWriter.listBackups(a, policy).stream().map(SafeFileWriterTest::read).toList())
                .containsExactly("name: A0");
        assertThat(SafeFileWriter.listBackups(b, policy).stream().map(SafeFileWriterTest::read).toList())
                .containsExactly("name: B2", "name: B1", "name: B0");
        assertThat(SafeFileWriter.restoreLatestBackup(a, policy)).isPresent();
        assertThat(Files.readString(a)).isEqualTo("name: A0");
        assertThat(Files.readString(b)).isEqualTo("name: B3");
    }

    @Test
    void keepsOlderBackupsWhenWritesFail() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("v1"));
        BackupPolicy policy = new BackupPolicy(directory.resolve("backups"), 3);
        for (int i = 2; i <= 4; i++) {
            SafeFileWriter.write(target, bytes("v" + i), null, policy);
        }
        SafeFileWriter.Mover failing = (source, destination) -> {
            throw new java.nio.file.AccessDeniedException(destination.toString());
        };
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> SafeFileWriter.write(target, bytes("v5"), null, policy, failing))
                    .isInstanceOf(java.nio.file.AccessDeniedException.class);
        }

        assertThat(Files.readString(target)).isEqualTo("v4");
        assertThat(SafeFileWriter.listBackups(target, policy).stream().map(SafeFileWriterTest::read).toList())
                .containsExactly("v3", "v2", "v1");
        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactlyInAnyOrder("config.yml",
                    "backups");
        }
        assertThat(SafeFileWriter.restoreLatestBackup(target, policy)).isPresent();
        assertThat(Files.readString(target)).isEqualTo("v3");
    }

    @Test
    void reusesTheNewestBackupWhenItHasTheCurrentContent() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("v0"));
        BackupPolicy policy = BackupPolicy.in(directory.resolve("backups"));
        Path first = SafeFileWriter.write(target, bytes("v1"), null, policy).backup();
        Files.write(target, bytes("v0"));

        SafeFileWriter.WriteResult result = SafeFileWriter.write(target, bytes("v2"), null, policy);

        assertThat(result.backup()).isEqualTo(first);
        assertThat(SafeFileWriter.listBackups(target, policy)).containsExactly(first);
        assertThat(Files.readString(target)).isEqualTo("v2");
    }

    @Test
    void replacesTheTargetOfASymbolicLink() throws Exception {
        Path shared = Files.createDirectories(directory.resolve("shared")).resolve("vouchers.yml");
        Files.write(shared, bytes("a: 1\n"));
        Path link = directory.resolve("config.yml");
        try {
            Files.createSymbolicLink(link, shared);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("Symbolic links cannot be created here: " + e);
        }
        BackupPolicy policy = BackupPolicy.in(directory.resolve("backups"));

        SafeFileWriter.write(link, bytes("a: 2\n"), Revisions.ofFile(link), policy);

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(shared)).isEqualTo("a: 2\n");
        assertThat(SafeFileWriter.listBackups(link, policy)).isEqualTo(SafeFileWriter.listBackups(shared, policy))
                .hasSize(1);
        try (Stream<Path> files = Files.list(shared.getParent())) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("vouchers.yml");
        }
    }

    @Test
    void keepsThePosixPermissionsOfTheReplacedFile() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("a: 1\n"));
        Assumptions.assumeTrue(Files.getFileAttributeView(target, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported here");
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(target, permissions);

        SafeFileWriter.write(target, bytes("a: 2\n"), null, BackupPolicy.in(directory.resolve("backups")));

        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(permissions);
    }

    @Test
    void restoresTheLatestBackup() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.write(target, bytes("first"));
        BackupPolicy policy = BackupPolicy.in(directory.resolve("backups"));
        SafeFileWriter.write(target, bytes("second"), null, policy);
        SafeFileWriter.write(target, bytes("third"), null, policy);
        assertThat(SafeFileWriter.restoreLatestBackup(target, policy)).isPresent();
        assertThat(Files.readString(target)).isEqualTo("second");
    }

    @Test
    void requiresAnExistingParentDirectory() {
        Path target = directory.resolve("missing").resolve("config.yml");
        assertThatThrownBy(() -> SafeFileWriter.write(target, bytes("x"), null,
                BackupPolicy.in(directory.resolve("backups")))).isInstanceOf(java.io.IOException.class);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
