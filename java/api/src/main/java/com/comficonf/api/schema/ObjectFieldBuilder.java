package com.comficonf.api.schema;

import java.util.Objects;

/**
 * Builder of {@link FieldType#OBJECT} fields. An object is always present; its value is a map of its child
 * fields. Objects at the top level of a schema are shown as sections of the editor.
 */
public final class ObjectFieldBuilder extends FieldBuilder<ObjectFieldBuilder> {

    ObjectFieldBuilder(String key) {
        super(key, FieldType.OBJECT);
    }

    @Override
    ObjectFieldBuilder self() {
        return this;
    }

    /** Adds child fields. */
    public ObjectFieldBuilder fields(FieldBuilder<?>... children) {
        for (FieldBuilder<?> child : children) {
            fields.add(Objects.requireNonNull(child, "child").build());
        }
        return this;
    }

    /** Adds a built child field. */
    public ObjectFieldBuilder field(ConfigField child) {
        fields.add(Objects.requireNonNull(child, "child"));
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
        if (!required) {
            throw fail("object fields are always present and cannot be optional");
        }
        if (fields.isEmpty()) {
            throw fail("needs at least one child field");
        }
        SchemaChecks.checkSiblings(fields, "object '" + key + "'");
    }

    @Override
    void validateDefault(Object value) {
        throw fail("object fields have no default value");
    }
}
