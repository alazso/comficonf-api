package com.comficonf.api.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Content-based revision tokens. A revision changes whenever the bytes change, whoever changed them. */
public final class Revisions {

    /** Revision of a file that does not exist. */
    public static final String ABSENT = "absent";

    private Revisions() {
    }

    /** Returns the lowercase hex SHA-256 digest of {@code content}. */
    public static String of(byte[] content) {
        Objects.requireNonNull(content, "content");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Returns the revision of the file's current content, or {@link #ABSENT} when it does not exist. */
    public static String ofFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try {
            return of(Files.readAllBytes(file));
        } catch (NoSuchFileException e) {
            return ABSENT;
        }
    }
}
