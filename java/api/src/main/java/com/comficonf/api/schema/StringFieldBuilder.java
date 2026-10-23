package com.comficonf.api.schema;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Builder of {@link FieldType#STRING} and {@link FieldType#TEXT} fields. */
public final class StringFieldBuilder extends FieldBuilder<StringFieldBuilder> {

    StringFieldBuilder(String key, FieldType type) {
        super(key, type);
    }

    @Override
    StringFieldBuilder self() {
        return this;
    }

    /** Minimum length in Unicode code points. */
    public StringFieldBuilder minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    /** Maximum length in Unicode code points. */
    public StringFieldBuilder maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /** Minimum and maximum length in Unicode code points. */
    public StringFieldBuilder length(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        return this;
    }

    /**
     * Regular expression the whole value must match. Use syntax that Java and JavaScript interpret the same way:
     * character classes, groups, alternation and quantifiers, without lookbehind, possessive quantifiers or
     * inline flags. At most 1000 characters.
     */
    public StringFieldBuilder pattern(String pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        return this;
    }

    /**
     * Regular expression the value must match, and the message shown when it does not (at most 300 characters).
     */
    public StringFieldBuilder pattern(String pattern, String message) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.patternMessage = Objects.requireNonNull(message, "message");
        return this;
    }

    /** Placeholder for the empty input. */
    public StringFieldBuilder placeholder(@Nullable String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public StringFieldBuilder defaultValue(@Nullable String value) {
        this.defaultValue = value;
        return this;
    }

    /**
     * Marks the value as secret. The bridge never sends it to the editor, which shows the field as set or unset
     * without a way to change it.
     */
    public StringFieldBuilder sensitive() {
        this.sensitive = true;
        return this;
    }

    /**
     * Marks the value as secret and lets the editor replace it without revealing the current value. Implies
     * {@link #sensitive()}.
     */
    public StringFieldBuilder writeOnly() {
        this.sensitive = true;
        this.writeOnly = true;
        return this;
    }

    @Override
    void validateType() {
        checkLengthRange();
        checkNoNumericAttributes();
        if (sensitive && defaultValue != null) {
            throw fail("sensitive fields cannot declare a default value");
        }
        if (sensitive && readOnly) {
            throw fail("sensitive fields are already hidden; readOnly does not apply");
        }
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof String text)) {
            throw fail("default value must be a string");
        }
        checkString(text);
    }
}
