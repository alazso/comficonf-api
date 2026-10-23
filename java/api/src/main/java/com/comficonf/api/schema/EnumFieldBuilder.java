package com.comficonf.api.schema;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Builder of {@link FieldType#ENUM} fields. */
public final class EnumFieldBuilder extends FieldBuilder<EnumFieldBuilder> {

    EnumFieldBuilder(String key) {
        super(key, FieldType.ENUM);
    }

    @Override
    EnumFieldBuilder self() {
        return this;
    }

    /** Adds a choice whose label is the value. */
    public EnumFieldBuilder choice(String value) {
        choices.add(Choice.of(value));
        return this;
    }

    /** Adds a choice with a label. */
    public EnumFieldBuilder choice(String value, String label) {
        choices.add(Choice.of(value, label));
        return this;
    }

    /** Adds a choice with a label and help text. */
    public EnumFieldBuilder choice(String value, String label, @Nullable String description) {
        choices.add(new Choice(value, label, description));
        return this;
    }

    /** Adds a choice. */
    public EnumFieldBuilder choice(Choice choice) {
        choices.add(Objects.requireNonNull(choice, "choice"));
        return this;
    }

    public EnumFieldBuilder defaultValue(@Nullable String value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
        if (choices.isEmpty()) {
            throw fail("needs at least one choice");
        }
        if (choices.size() > 500) {
            throw fail("supports at most 500 choices");
        }
        checkChoicesUnique();
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof String text) || choices.stream().noneMatch(c -> c.value().equals(text))) {
            throw fail("default value must be one of the choices");
        }
    }
}
