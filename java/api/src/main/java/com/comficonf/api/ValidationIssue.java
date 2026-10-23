package com.comficonf.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validation error or warning attached to a value.
 *
 * @param path path of the affected value, see {@link ConfigPath}; the empty path refers to the whole resource
 * @param severity whether the issue blocks the change
 * @param message message shown to the server owner, at most 500 characters
 * @param code machine-readable code of 1 to 64 characters of lowercase letters, digits and {@code _}
 */
public record ValidationIssue(String path, Severity severity, String message, String code) {

    /** Code used when the plugin does not specify one. */
    public static final String PLUGIN_CODE = "plugin";

    private static final Pattern CODE = Pattern.compile("[a-z0-9_]{1,64}");
    private static final int MAX_MESSAGE = 500;

    public ValidationIssue {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(code, "code");
        if (!path.isEmpty()) {
            ConfigPath.parse(path);
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = Texts.truncate(message, MAX_MESSAGE);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid issue code '" + code + "'");
        }
    }

    /** Creates an error with the {@link #PLUGIN_CODE plugin} code. */
    public static ValidationIssue error(String path, String message) {
        return new ValidationIssue(path, Severity.ERROR, message, PLUGIN_CODE);
    }

    /** Creates a warning with the {@link #PLUGIN_CODE plugin} code. */
    public static ValidationIssue warning(String path, String message) {
        return new ValidationIssue(path, Severity.WARNING, message, PLUGIN_CODE);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
