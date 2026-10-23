package com.comficonf.api.schema;

import com.comficonf.api.ConfigPath;
import com.comficonf.api.ConfigValues;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Shows a field only while a sibling field (a field of the same object) has certain values. Hidden fields keep
 * their values and are still submitted; the editor only hides them.
 */
public final class VisibilityCondition {

    /** Comparison applied to the sibling value. */
    public enum Operator {
        /** Visible when the sibling equals the single value. */
        EQUALS("equals"),
        /** Visible when the sibling differs from the single value. */
        NOT_EQUALS("notEquals"),
        /** Visible when the sibling equals one of the values. */
        IN("in"),
        /** Visible when the sibling equals none of the values. */
        NOT_IN("notIn");

        private final String protocolName;

        Operator(String protocolName) {
            this.protocolName = protocolName;
        }

        /** Name used in the wire protocol. */
        public String protocolName() {
            return protocolName;
        }
    }

    private final String field;
    private final Operator operator;
    private final List<Object> values;

    private VisibilityCondition(String field, Operator operator, List<Object> values) {
        if (!ConfigPath.isValidKey(field)) {
            throw new SchemaException("Invalid visibility condition field '" + field + "'");
        }
        if (values.isEmpty()) {
            throw new SchemaException("A visibility condition needs at least one value");
        }
        List<Object> normalized = new ArrayList<>(values.size());
        for (Object value : values) {
            Object scalar = ConfigValues.normalizeValue(Objects.requireNonNull(value, "value"));
            if (!(scalar instanceof String || scalar instanceof Boolean || scalar instanceof Long
                    || scalar instanceof Double)) {
                throw new SchemaException("Visibility condition values must be strings, booleans or numbers");
            }
            normalized.add(scalar);
        }
        this.field = field;
        this.operator = operator;
        this.values = List.copyOf(normalized);
    }

    /** Visible while {@code field} equals {@code value}. */
    public static VisibilityCondition whenEquals(String field, Object value) {
        return new VisibilityCondition(field, Operator.EQUALS, List.of(value));
    }

    /** Visible while {@code field} differs from {@code value}. */
    public static VisibilityCondition whenNotEquals(String field, Object value) {
        return new VisibilityCondition(field, Operator.NOT_EQUALS, List.of(value));
    }

    /** Visible while {@code field} equals one of {@code values}. */
    public static VisibilityCondition whenIn(String field, Object... values) {
        return new VisibilityCondition(field, Operator.IN, Arrays.asList(values));
    }

    /** Visible while {@code field} equals none of {@code values}. */
    public static VisibilityCondition whenNotIn(String field, Object... values) {
        return new VisibilityCondition(field, Operator.NOT_IN, Arrays.asList(values));
    }

    /** Key of the sibling field the condition reads. */
    public String field() {
        return field;
    }

    public Operator operator() {
        return operator;
    }

    /** Compared values: one value for {@code EQUALS}/{@code NOT_EQUALS}, one or more otherwise. */
    public List<Object> values() {
        return values;
    }

    /** Evaluates the condition against the value of the sibling field. */
    public boolean test(@Nullable Object siblingValue) {
        Object value = ConfigValues.normalizeValue(siblingValue);
        boolean matches = values.contains(value);
        return switch (operator) {
            case EQUALS, IN -> matches;
            case NOT_EQUALS, NOT_IN -> !matches;
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VisibilityCondition that && field.equals(that.field)
                && operator == that.operator && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, values);
    }

    @Override
    public String toString() {
        return field + " " + operator.protocolName() + " " + values;
    }
}
