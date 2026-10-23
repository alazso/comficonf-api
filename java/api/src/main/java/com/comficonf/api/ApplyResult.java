package com.comficonf.api;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result returned by {@link ConfigProvider#apply}. Immutable.
 *
 * <p>The factory methods enforce the meaning of each {@link ApplyStatus}: only {@link #applied} reports values
 * as both persisted and active.
 */
public final class ApplyResult {

    private static final int MAX_MESSAGE = 1000;

    private final ApplyStatus status;
    private final @Nullable String message;
    private final @Nullable String newRevision;
    private final @Nullable String currentRevision;
    private final boolean persisted;
    private final boolean activated;
    private final List<ValidationIssue> issues;
    private final List<String> restartRequired;

    private ApplyResult(ApplyStatus status, @Nullable String message, @Nullable String newRevision,
            @Nullable String currentRevision, boolean persisted, boolean activated, List<ValidationIssue> issues,
            List<String> restartRequired) {
        this.status = status;
        this.message = message == null ? null : Texts.truncate(message, MAX_MESSAGE);
        this.newRevision = newRevision;
        this.currentRevision = currentRevision;
        this.persisted = persisted;
        this.activated = activated;
        this.issues = issues;
        this.restartRequired = restartRequired;
    }

    /** The values were persisted under {@code newRevision} and every change is active at runtime. */
    public static ApplyResult applied(String newRevision) {
        return applied(newRevision, null);
    }

    /** The values were persisted under {@code newRevision} and every change is active at runtime. */
    public static ApplyResult applied(String newRevision, @Nullable String message) {
        return new ApplyResult(ApplyStatus.APPLIED, message, requireRevision(newRevision), null, true, true,
                List.of(), List.of());
    }

    /**
     * The values were persisted under {@code newRevision}, but not every change is active until the server
     * restarts.
     *
     * @param message explanation shown to the server owner, for example which reload step failed
     */
    public static ApplyResult savedRestartRequired(String newRevision, String message) {
        return savedRestartRequired(newRevision, message, List.of());
    }

    /**
     * The values were persisted under {@code newRevision}, but the changes at {@code restartRequiredPaths} are
     * not active until the server restarts.
     */
    public static ApplyResult savedRestartRequired(String newRevision, String message,
            Collection<String> restartRequiredPaths) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(restartRequiredPaths, "restartRequiredPaths");
        for (String path : restartRequiredPaths) {
            ConfigPath.parse(path);
        }
        return new ApplyResult(ApplyStatus.SAVED_RESTART_REQUIRED, message, requireRevision(newRevision), null,
                true, false, List.of(), List.copyOf(restartRequiredPaths));
    }

    /**
     * The values were rejected. Nothing was persisted.
     *
     * @throws IllegalArgumentException if {@code validation} has no errors
     */
    public static ApplyResult rejected(ValidationResult validation) {
        Objects.requireNonNull(validation, "validation");
        if (validation.isValid()) {
            throw new IllegalArgumentException("A rejected result needs at least one error");
        }
        return new ApplyResult(ApplyStatus.REJECTED, null, null, null, false, false, validation.issues(),
                List.of());
    }

    /** The values were rejected with a resource-level error. Nothing was persisted. */
    public static ApplyResult rejected(String message) {
        return rejected(ValidationResult.error("", message));
    }

    /**
     * The stored values no longer match the revision the editor started from. Nothing was persisted.
     *
     * @param currentRevision revision of the stored values
     */
    public static ApplyResult conflict(String currentRevision) {
        return new ApplyResult(ApplyStatus.CONFLICT, null, null, requireRevision(currentRevision), false, false,
                List.of(), List.of());
    }

    /** The operation failed before anything was persisted. */
    public static ApplyResult failed(String message) {
        Objects.requireNonNull(message, "message");
        return new ApplyResult(ApplyStatus.FAILED, message, null, null, false, false, List.of(), List.of());
    }

    public ApplyStatus status() {
        return status;
    }

    public @Nullable String message() {
        return message;
    }

    /** Revision of the persisted values, for {@code APPLIED} and {@code SAVED_RESTART_REQUIRED}. */
    public @Nullable String newRevision() {
        return newRevision;
    }

    /** Revision of the stored values, for {@code CONFLICT}. */
    public @Nullable String currentRevision() {
        return currentRevision;
    }

    /** Whether the values were written to storage. */
    public boolean persisted() {
        return persisted;
    }

    /** Whether every change is active at runtime. */
    public boolean activated() {
        return activated;
    }

    /** Validation issues, for {@code REJECTED}. */
    public List<ValidationIssue> issues() {
        return issues;
    }

    /** Paths of changes that take effect after a restart, for {@code SAVED_RESTART_REQUIRED}. */
    public List<String> restartRequired() {
        return restartRequired;
    }

    @Override
    public String toString() {
        return "ApplyResult[" + status + (message == null ? "" : ", " + message) + "]";
    }

    private static String requireRevision(String revision) {
        Objects.requireNonNull(revision, "revision");
        if (revision.isEmpty() || revision.length() > 128) {
            throw new IllegalArgumentException("revision must be 1-128 characters");
        }
        return revision;
    }
}
