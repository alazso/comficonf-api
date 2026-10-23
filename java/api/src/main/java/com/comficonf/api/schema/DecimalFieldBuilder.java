package com.comficonf.api.schema;

import java.util.Objects;

/** Builder of {@link FieldType#DECIMAL} fields. */
public final class DecimalFieldBuilder extends FieldBuilder<DecimalFieldBuilder> {

    DecimalFieldBuilder(String key) {
        super(key, FieldType.DECIMAL);
    }

    @Override
    DecimalFieldBuilder self() {
        return this;
    }

    /** Inclusive minimum. */
    public DecimalFieldBuilder min(double min) {
        this.min = min;
        return this;
    }

    /** Inclusive maximum. */
    public DecimalFieldBuilder max(double max) {
        this.max = max;
        return this;
    }

    /** Inclusive minimum and maximum. */
    public DecimalFieldBuilder range(double min, double max) {
        this.min = min;
        this.max = max;
        return this;
    }

    /** Increment of the editor's controls, such as {@code 0.1}. */
    public DecimalFieldBuilder step(double step) {
        this.step = step;
        return this;
    }

    /** Unit shown next to the input, such as {@code ×}. */
    public DecimalFieldBuilder unit(String unit) {
        this.unit = Objects.requireNonNull(unit, "unit");
        return this;
    }

    /** Shows a slider next to the input. Requires a minimum and a maximum. */
    public DecimalFieldBuilder slider() {
        this.slider = true;
        return this;
    }

    public DecimalFieldBuilder defaultValue(double value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    void validateType() {
        checkFinite("min", min);
        checkFinite("max", max);
        checkFinite("step", step);
        if (min != null && max != null && min.doubleValue() > max.doubleValue()) {
            throw fail("min must not exceed max");
        }
        if (step != null && step.doubleValue() <= 0) {
            throw fail("step must be positive");
        }
        if (slider && (min == null || max == null)) {
            throw fail("a slider needs both min and max");
        }
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof Double number) || !Double.isFinite(number)) {
            throw fail("default value must be a finite decimal");
        }
        if (min != null && number < min.doubleValue() || max != null && number > max.doubleValue()) {
            throw fail("default value is outside the allowed range");
        }
    }

    private void checkFinite(String name, java.lang.@org.jspecify.annotations.Nullable Number value) {
        if (value != null && !Double.isFinite(value.doubleValue())) {
            throw fail(name + " must be finite");
        }
    }
}
