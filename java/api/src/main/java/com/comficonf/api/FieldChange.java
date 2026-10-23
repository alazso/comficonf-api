package com.comficonf.api;

import com.comficonf.api.schema.ApplyMode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One difference between the current and the proposed values.
 *
 * @param path path of the changed value, see {@link ConfigPath}; inside object lists it uses the item index in
 *     the proposed list, or in the current list for removed items
 * @param kind kind of change
 * @param applyMode how the changed field takes effect
 * @param sensitive whether the field is sensitive; {@code before} and {@code after} are then always null
 * @param itemKey value of the item key field when the change is inside an object list that declares one
 * @param before previous value of a scalar or string list field; null for added items, containers and
 *     sensitive fields
 * @param after new value of a scalar or string list field; null for removed items, containers and sensitive
 *     fields
 */
public record FieldChange(
        String path,
        ChangeKind kind,
        ApplyMode applyMode,
        boolean sensitive,
        @Nullable String itemKey,
        @Nullable Object before,
        @Nullable Object after) {

    public FieldChange {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(applyMode, "applyMode");
        if (sensitive) {
            before = null;
            after = null;
        }
    }
}
