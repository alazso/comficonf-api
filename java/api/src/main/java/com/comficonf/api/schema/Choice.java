package com.comficonf.api.schema;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One selectable value of an {@link FieldType#ENUM} or {@link FieldType#STRING_LIST} field.
 *
 * @param value stored value, 1 to 128 characters
 * @param label text shown in the editor, at most 80 characters
 * @param description optional help text, at most 300 characters
 */
public record Choice(String value, String label, @Nullable String description) {

    public Choice {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        if (value.isEmpty() || value.length() > 128) {
            throw new SchemaException("Choice value must be 1-128 characters: '" + value + "'");
        }
        if (label.isBlank() || label.length() > 80) {
            throw new SchemaException("Choice label must be 1-80 characters: '" + label + "'");
        }
        if (description != null && description.length() > 300) {
            throw new SchemaException("Choice description must be at most 300 characters");
        }
    }

    /** Creates a choice whose label is the value. */
    public static Choice of(String value) {
        return new Choice(value, value, null);
    }

    /** Creates a choice with a label. */
    public static Choice of(String value, String label) {
        return new Choice(value, label, null);
    }
}
