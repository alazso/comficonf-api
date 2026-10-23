package com.comficonf.api;

import com.comficonf.api.schema.ConfigSchema;

/**
 * Callbacks through which the bridge describes, reads, validates and applies a configuration resource.
 *
 * <p>The plugin keeps ownership of its configuration storage: the bridge never touches configuration files
 * itself. Callbacks run on the thread selected with {@link ConfigResource.Builder#callbackThread}. The bridge
 * never invokes two callbacks of the same resource concurrently.
 *
 * <p>Exceptions thrown by a callback are reported to the editor as a failure of that operation, naming only the
 * callback and the exception class. The exception's message and stack trace are written to the server log and
 * never sent to the backend. To show the server owner an explanation, return it in a {@link ValidationResult} or
 * {@link ApplyResult}.
 */
public interface ConfigProvider {

    /**
     * Describes the editable fields. Called whenever the editor opens the resource and before every validate or
     * apply operation, so the schema may change at runtime (for example a choice list built from loaded data).
     */
    ConfigSchema describe();

    /**
     * Reads the current editable values and the revision identifying that state.
     *
     * <p>The revision must change whenever the persisted values change, including edits made outside ComfiConf
     * such as a manual file edit. A content hash of the backing file satisfies this, see
     * {@link com.comficonf.api.io.Revisions}.
     */
    ConfigSnapshot read() throws Exception;

    /**
     * Validates proposed values with plugin-specific rules.
     *
     * <p>The bridge has already checked {@code proposed} against the schema: it contains exactly the declared
     * fields with values of the declared types that satisfy the declared constraints. The only exception is a
     * sensitive field that the submission left out or set to null: it holds the value {@link #read()} returned,
     * unchanged and unchecked. This method only needs to check rules the schema cannot express, such as
     * cross-field rules.
     *
     * @param proposed the complete proposed values, including unchanged and sensitive fields
     */
    default ValidationResult validate(ConfigValues proposed) throws Exception {
        return ValidationResult.ok();
    }

    /**
     * Persists and activates validated values.
     *
     * <p>Called only after the revision check, schema validation and {@link #validate} succeeded. The
     * implementation must persist {@link ApplyContext#proposed()} as one operation, preserve stored data that is
     * not part of the schema, and report the outcome truthfully: {@link ApplyResult#applied} only when the
     * values are persisted and active at runtime, {@link ApplyResult#savedRestartRequired} when they are
     * persisted but some changes take effect only after a restart or the runtime reload failed.
     *
     * <p>The implementation should re-check {@link ApplyContext#baseRevision()} against the stored state
     * immediately before writing and return {@link ApplyResult#conflict} on mismatch, because the backing file
     * can change outside ComfiConf at any time. {@link com.comficonf.api.io.SafeFileWriter} performs that check.
     */
    ApplyResult apply(ApplyContext context) throws Exception;
}
