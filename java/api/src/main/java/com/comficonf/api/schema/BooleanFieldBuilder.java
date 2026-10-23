package com.comficonf.api.schema;

/** Builder of {@link FieldType#BOOLEAN} fields. */
public final class BooleanFieldBuilder extends FieldBuilder<BooleanFieldBuilder> {

    BooleanFieldBuilder(String key) {
        super(key, FieldType.BOOLEAN);
    }

    @Override
    BooleanFieldBuilder self() {
        return this;
    }

    public BooleanFieldBuilder defaultValue(boolean value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof Boolean)) {
            throw fail("default value must be a boolean");
        }
    }
}
