package com.comficonf.api;

import java.util.Objects;

/**
 * Current editable values of a resource and the revision identifying that state.
 *
 * @param values the editable values, containing the fields declared by the schema
 * @param revision opaque token of 1 to 128 printable ASCII characters without spaces that changes whenever the
 *     values change
 */
public record ConfigSnapshot(ConfigValues values, String revision) {

    public ConfigSnapshot {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(revision, "revision");
        if (revision.isEmpty() || revision.length() > 128
                || !revision.chars().allMatch(c -> c > 32 && c < 127)) {
            throw new IllegalArgumentException(
                    "revision must be 1-128 printable ASCII characters without spaces");
        }
    }

    public static ConfigSnapshot of(ConfigValues values, String revision) {
        return new ConfigSnapshot(values, revision);
    }
}
