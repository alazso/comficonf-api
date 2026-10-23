package com.comficonf.api.schema;

import com.comficonf.api.ConfigPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of a resource's editable fields.
 *
 * <pre>{@code
 * ConfigSchema schema = ConfigSchema.of(
 *         ConfigField.object("settings").label("General").fields(
 *                 ConfigField.string("prefix").maxLength(64),
 *                 ConfigField.bool("announce-redemptions")),
 *         ConfigField.objectList("vouchers").itemKey("id").itemTitle("display-name").fields(
 *                 ConfigField.string("id").pattern("[a-z0-9_-]+"),
 *                 ConfigField.string("display-name"),
 *                 ConfigField.integer("cooldown-seconds").min(0).unit("seconds")));
 * }</pre>
 *
 * <p>Top-level {@link FieldType#OBJECT} fields are shown as sections; other top-level fields are grouped in a
 * general section.
 */
public final class ConfigSchema {

    /** Version of the serialized schema format sent to the editor. */
    public static final int FORMAT_VERSION = 1;
    /** Maximum nesting depth of fields. */
    public static final int MAX_DEPTH = 8;
    /** Maximum number of fields, counting nested fields. */
    public static final int MAX_FIELDS = 2000;

    private final List<ConfigField> fields;

    private ConfigSchema(List<ConfigField> fields) {
        if (fields.isEmpty()) {
            throw new SchemaException("A schema needs at least one field");
        }
        SchemaChecks.checkSiblings(fields, "the schema root");
        int count = 0;
        for (ConfigField field : fields) {
            if (field.depth() > MAX_DEPTH) {
                throw new SchemaException("Field '" + field.key() + "' is nested deeper than " + MAX_DEPTH
                        + " levels");
            }
            count += field.fieldCount();
        }
        if (count > MAX_FIELDS) {
            throw new SchemaException("A schema supports at most " + MAX_FIELDS + " fields, found " + count);
        }
        this.fields = List.copyOf(fields);
    }

    /**
     * Builds a schema from field builders.
     *
     * @throws SchemaException if a field or the combination of fields is invalid
     */
    public static ConfigSchema of(FieldBuilder<?>... fields) {
        Builder builder = builder();
        for (FieldBuilder<?> field : fields) {
            builder.field(field);
        }
        return builder.build();
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Top-level fields in declaration order. */
    public List<ConfigField> fields() {
        return fields;
    }

    /** Returns the top-level field with {@code key}. */
    public Optional<ConfigField> field(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    /**
     * Returns the field that defines the value at {@code path}. List indexes in the path are resolved to the
     * list field's item definition, so {@code vouchers[3].cooldown} resolves to the {@code cooldown} item field
     * and {@code worlds[0]} to the {@code worlds} string list field.
     *
     * @throws IllegalArgumentException if the path is malformed
     */
    public Optional<ConfigField> fieldAt(String path) {
        List<Object> segments = ConfigPath.parse(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        List<ConfigField> container = fields;
        ConfigField current = null;
        for (int i = 0; i < segments.size(); i++) {
            Object segment = segments.get(i);
            if (segment instanceof Integer) {
                if (current == null || current.type() != FieldType.OBJECT_LIST
                        && current.type() != FieldType.STRING_LIST) {
                    return Optional.empty();
                }
                if (current.type() == FieldType.STRING_LIST && i != segments.size() - 1) {
                    return Optional.empty();
                }
                continue;
            }
            if (current != null) {
                boolean afterIndex = segments.get(i - 1) instanceof Integer;
                if (current.type() == FieldType.OBJECT_LIST && !afterIndex
                        || current.type() != FieldType.OBJECT && current.type() != FieldType.OBJECT_LIST) {
                    return Optional.empty();
                }
                container = current.fields();
            }
            String key = (String) segment;
            current = container.stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(current);
    }

    /** Number of fields, counting nested fields. */
    public int fieldCount() {
        int count = 0;
        for (ConfigField field : fields) {
            count += field.fieldCount();
        }
        return count;
    }

    @Override
    public String toString() {
        return "ConfigSchema" + fields;
    }

    /** Builder of {@link ConfigSchema}. Not thread-safe. */
    public static final class Builder {
        private final List<ConfigField> fields = new ArrayList<>();

        private Builder() {
        }

        /** Adds a top-level field. */
        public Builder field(FieldBuilder<?> field) {
            fields.add(Objects.requireNonNull(field, "field").build());
            return this;
        }

        /** Adds a built top-level field. */
        public Builder field(ConfigField field) {
            fields.add(Objects.requireNonNull(field, "field"));
            return this;
        }

        /**
         * Builds the schema.
         *
         * @throws SchemaException if the combination of fields is invalid
         */
        public ConfigSchema build() {
            return new ConfigSchema(fields);
        }
    }
}
