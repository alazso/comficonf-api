package com.comficonf.api.schema;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Builder of {@link FieldType#STRING_LIST} fields. With choices the editor shows a multi-select and every item
 * must be one of the choices; without choices it shows a free-form tag input.
 */
public final class StringListFieldBuilder extends FieldBuilder<StringListFieldBuilder> {

    StringListFieldBuilder(String key) {
        super(key, FieldType.STRING_LIST);
    }

    @Override
    StringListFieldBuilder self() {
        return this;
    }

    /** Adds an allowed item whose label is the value. */
    public StringListFieldBuilder choice(String value) {
        choices.add(Choice.of(value));
        return this;
    }

    /** Adds an allowed item with a label. */
    public StringListFieldBuilder choice(String value, String label) {
        choices.add(Choice.of(value, label));
        return this;
    }

    /** Adds an allowed item with a label and help text. */
    public StringListFieldBuilder choice(String value, String label, @Nullable String description) {
        choices.add(new Choice(value, label, description));
        return this;
    }

    public StringListFieldBuilder minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    public StringListFieldBuilder maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    /** Requires the items to be distinct. */
    public StringListFieldBuilder uniqueItems() {
        this.uniqueItems = true;
        return this;
    }

    /** Sets whether item order is meaningful. Defaults to true. */
    public StringListFieldBuilder ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    /** Minimum length of each item in Unicode code points. */
    public StringListFieldBuilder itemMinLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    /** Maximum length of each item in Unicode code points. */
    public StringListFieldBuilder itemMaxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /**
     * Regular expression each item must match, with the same syntax and length limits as
     * {@link StringFieldBuilder#pattern(String, String)}.
     */
    public StringListFieldBuilder itemPattern(String pattern, String message) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.patternMessage = Objects.requireNonNull(message, "message");
        return this;
    }

    /** Placeholder of the item input. */
    public StringListFieldBuilder placeholder(@Nullable String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public StringListFieldBuilder defaultValue(@Nullable List<String> value) {
        this.defaultValue = value == null ? null : List.copyOf(value);
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
        checkLengthRange();
        checkItemRange();
        checkChoicesUnique();
        if (choices.size() > 500) {
            throw fail("supports at most 500 choices");
        }
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof List<?> list)) {
            throw fail("default value must be a list of strings");
        }
        if (minItems != null && list.size() < minItems || maxItems != null && list.size() > maxItems) {
            throw fail("default value has an invalid number of items");
        }
        for (Object item : list) {
            if (!(item instanceof String text)) {
                throw fail("default value must be a list of strings");
            }
            if (!choices.isEmpty() && choices.stream().noneMatch(c -> c.value().equals(text))) {
                throw fail("default value contains an item that is not a choice");
            }
            checkString(text);
        }
        if (uniqueItems && list.stream().distinct().count() != list.size()) {
            throw fail("default value contains duplicate items");
        }
    }
}
