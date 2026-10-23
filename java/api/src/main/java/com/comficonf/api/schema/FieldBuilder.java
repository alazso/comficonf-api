package com.comficonf.api.schema;

import com.comficonf.api.ConfigPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Common builder of {@link ConfigField}. Type-specific subclasses add the attributes of their type. Not
 * thread-safe.
 *
 * @param <B> the concrete builder type
 */
public abstract class FieldBuilder<B extends FieldBuilder<B>> {

    static final int MAX_LABEL = 80;
    static final int MAX_DESCRIPTION = 1000;
    static final int MAX_PLACEHOLDER = 200;
    static final int MAX_UNIT = 24;
    static final int MAX_PATTERN = 1000;
    static final int MAX_PATTERN_MESSAGE = 300;
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    final String key;
    final FieldType type;
    @Nullable String label;
    @Nullable String description;
    @Nullable String placeholder;
    boolean required = true;
    boolean readOnly;
    boolean sensitive;
    boolean writeOnly;
    ApplyMode applyMode = ApplyMode.LIVE;
    @Nullable VisibilityCondition visibleWhen;
    @Nullable Object defaultValue;
    @Nullable Integer minLength;
    @Nullable Integer maxLength;
    @Nullable String pattern;
    @Nullable String patternMessage;
    @Nullable Number min;
    @Nullable Number max;
    @Nullable Number step;
    @Nullable String unit;
    boolean slider;
    final List<Choice> choices = new ArrayList<>();
    @Nullable Integer minItems;
    @Nullable Integer maxItems;
    boolean uniqueItems;
    boolean ordered = true;
    @Nullable MaterialFilter materialFilter;
    final List<ConfigField> fields = new ArrayList<>();
    @Nullable String itemKey;
    @Nullable String itemTitle;
    @Nullable String itemLabel;

    FieldBuilder(String key, FieldType type) {
        if (!ConfigPath.isValidKey(key)) {
            throw new SchemaException("Invalid field key '" + key + "': use 1-64 letters, digits, '_' or '-'");
        }
        this.key = key;
        this.type = type;
    }

    abstract B self();

    /** Text shown as the field's label, at most 80 characters. Defaults to a label derived from the key. */
    public B label(String label) {
        this.label = Objects.requireNonNull(label, "label");
        return self();
    }

    /** Help text shown below the field, at most 1000 characters. */
    public B description(@Nullable String description) {
        this.description = description;
        return self();
    }

    /** Allows the value to be absent or null. */
    public B optional() {
        this.required = false;
        return self();
    }

    /** Sets whether a value must be present. Fields are required by default. */
    public B required(boolean required) {
        this.required = required;
        return self();
    }

    /** Shows the value without allowing changes from the editor. */
    public B readOnly() {
        this.readOnly = true;
        return self();
    }

    /** Declares that changes to this field take effect only after a server restart. */
    public B restartRequired() {
        this.applyMode = ApplyMode.RESTART;
        return self();
    }

    /** Sets how changes to this field take effect. Defaults to {@link ApplyMode#LIVE}. */
    public B applyMode(ApplyMode applyMode) {
        this.applyMode = Objects.requireNonNull(applyMode, "applyMode");
        return self();
    }

    /** Shows the field only while the condition on a sibling field holds. */
    public B visibleWhen(VisibilityCondition condition) {
        this.visibleWhen = Objects.requireNonNull(condition, "condition");
        return self();
    }

    /** Shows the field only while the sibling field {@code field} equals {@code value}. */
    public B visibleWhen(String field, Object value) {
        return visibleWhen(VisibilityCondition.whenEquals(field, value));
    }

    /**
     * Builds the field.
     *
     * @throws SchemaException if the definition is inconsistent
     */
    public final ConfigField build() {
        String resolvedLabel = label == null ? deriveLabel(key) : label;
        checkText("label", resolvedLabel, MAX_LABEL, false);
        checkText("description", description, MAX_DESCRIPTION, true);
        checkText("placeholder", placeholder, MAX_PLACEHOLDER, true);
        checkText("unit", unit, MAX_UNIT, true);
        if (visibleWhen != null && visibleWhen.field().equals(key)) {
            throw fail("visibility condition must refer to a sibling field, not to the field itself");
        }
        validateType();
        if (defaultValue != null) {
            validateDefault(defaultValue);
        }
        return new ConfigField(this, resolvedLabel);
    }

    /** Checks the type-specific attributes. */
    abstract void validateType();

    /** Checks that {@code value} is a valid value of this field. */
    abstract void validateDefault(Object value);

    SchemaException fail(String message) {
        return new SchemaException("Field '" + key + "' (" + type.protocolName() + "): " + message);
    }

    void checkLengthRange() {
        if (minLength != null && minLength < 0) {
            throw fail("minLength must not be negative");
        }
        if (maxLength != null && maxLength < 1) {
            throw fail("maxLength must be at least 1");
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw fail("minLength must not exceed maxLength");
        }
        if (pattern != null) {
            if (pattern.length() > MAX_PATTERN) {
                throw fail("pattern must be at most " + MAX_PATTERN + " characters");
            }
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException e) {
                throw fail("pattern is not a valid regular expression: " + e.getDescription());
            }
        }
        if (patternMessage != null && patternMessage.length() > MAX_PATTERN_MESSAGE) {
            throw fail("pattern message must be at most " + MAX_PATTERN_MESSAGE + " characters");
        }
    }

    void checkItemRange() {
        if (minItems != null && minItems < 0) {
            throw fail("minItems must not be negative");
        }
        if (maxItems != null && maxItems < 1) {
            throw fail("maxItems must be at least 1");
        }
        if (minItems != null && maxItems != null && minItems > maxItems) {
            throw fail("minItems must not exceed maxItems");
        }
    }

    void checkString(String value) {
        int length = value.codePointCount(0, value.length());
        if (minLength != null && length < minLength) {
            throw fail("default value is shorter than minLength");
        }
        if (maxLength != null && length > maxLength) {
            throw fail("default value is longer than maxLength");
        }
        if (pattern != null && !java.util.regex.Pattern.compile(pattern).matcher(value).matches()) {
            throw fail("default value does not match the pattern");
        }
    }

    void checkChoicesUnique() {
        long distinct = choices.stream().map(Choice::value).distinct().count();
        if (distinct != choices.size()) {
            throw fail("choice values must be unique");
        }
    }

    void checkNoNumericAttributes() {
        if (min != null || max != null || step != null || unit != null || slider) {
            throw fail("numeric attributes do not apply");
        }
    }

    private void checkText(String name, @Nullable String value, int maxLength, boolean optional) {
        if (value == null) {
            if (!optional) {
                throw fail(name + " must not be null");
            }
            return;
        }
        if (!optional && value.isBlank()) {
            throw fail(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw fail(name + " must be at most " + maxLength + " characters");
        }
    }

    /** Turns {@code cooldown-seconds}, {@code cooldown_seconds} or {@code cooldownSeconds} into "Cooldown seconds". */
    static String deriveLabel(String key) {
        StringBuilder words = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '-' || c == '_') {
                if (!words.isEmpty() && words.charAt(words.length() - 1) != ' ') {
                    words.append(' ');
                }
            } else {
                if (Character.isUpperCase(c) && Character.isLowerCase(previous)) {
                    words.append(' ');
                }
                words.append(Character.toLowerCase(c));
            }
            previous = c;
        }
        String text = words.toString().trim();
        if (text.isEmpty()) {
            return key;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
