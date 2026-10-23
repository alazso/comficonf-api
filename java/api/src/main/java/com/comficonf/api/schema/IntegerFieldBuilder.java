package com.comficonf.api.schema;

import java.util.Objects;

/** Builder of {@link FieldType#INTEGER} fields. Values are limited to ±(2<sup>53</sup> - 1). */
public final class IntegerFieldBuilder extends FieldBuilder<IntegerFieldBuilder> {

    IntegerFieldBuilder(String key) {
        super(key, FieldType.INTEGER);
    }

    @Override
    IntegerFieldBuilder self() {
        return this;
    }

    /** Inclusive minimum. */
    public IntegerFieldBuilder min(long min) {
        this.min = min;
        return this;
    }

    /** Inclusive maximum. */
    public IntegerFieldBuilder max(long max) {
        this.max = max;
        return this;
    }

    /** Inclusive minimum and maximum. */
    public IntegerFieldBuilder range(long min, long max) {
        this.min = min;
        this.max = max;
        return this;
    }

    /** Increment of the editor's controls. Defaults to 1. */
    public IntegerFieldBuilder step(long step) {
        this.step = step;
        return this;
    }

    /** Unit shown next to the input, such as {@code seconds}. */
    public IntegerFieldBuilder unit(String unit) {
        this.unit = Objects.requireNonNull(unit, "unit");
        return this;
    }

    /** Shows a slider next to the input. Requires a minimum and a maximum. */
    public IntegerFieldBuilder slider() {
        this.slider = true;
        return this;
    }

    public IntegerFieldBuilder defaultValue(long value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    void validateType() {
        checkSafe("min", min);
        checkSafe("max", max);
        checkSafe("step", step);
        if (min != null && max != null && min.longValue() > max.longValue()) {
            throw fail("min must not exceed max");
        }
        if (step != null && step.longValue() < 1) {
            throw fail("step must be at least 1");
        }
        if (slider && (min == null || max == null)) {
            throw fail("a slider needs both min and max");
        }
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof Long number)) {
            throw fail("default value must be an integer");
        }
        checkSafe("default value", number);
        if (min != null && number < min.longValue() || max != null && number > max.longValue()) {
            throw fail("default value is outside the allowed range");
        }
    }

    /** Whether {@code value} is within ±(2<sup>53</sup> - 1). */
    static boolean isSafe(long value) {
        return value >= -MAX_SAFE_INTEGER && value <= MAX_SAFE_INTEGER;
    }

    private void checkSafe(String name, java.lang.@org.jspecify.annotations.Nullable Number value) {
        if (value != null && !isSafe(value.longValue())) {
            throw fail(name + " must be within ±9007199254740991");
        }
    }
}
