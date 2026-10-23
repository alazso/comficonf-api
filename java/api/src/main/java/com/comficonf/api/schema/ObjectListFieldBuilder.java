package com.comficonf.api.schema;

import java.util.Objects;

/**
 * Builder of {@link FieldType#OBJECT_LIST} fields: repeatable items that share the same fields. The editor
 * supports adding, removing, duplicating and reordering items, and bulk-editing a field across selected items.
 */
public final class ObjectListFieldBuilder extends FieldBuilder<ObjectListFieldBuilder> {

    ObjectListFieldBuilder(String key) {
        super(key, FieldType.OBJECT_LIST);
    }

    @Override
    ObjectListFieldBuilder self() {
        return this;
    }

    /** Adds item fields. */
    public ObjectListFieldBuilder fields(FieldBuilder<?>... children) {
        for (FieldBuilder<?> child : children) {
            fields.add(Objects.requireNonNull(child, "child").build());
        }
        return this;
    }

    /** Adds a built item field. */
    public ObjectListFieldBuilder field(ConfigField child) {
        fields.add(Objects.requireNonNull(child, "child"));
        return this;
    }

    /**
     * Declares the required {@link FieldType#STRING} item field whose value identifies an item. Values must be
     * unique within the list. The editor and change summaries use it to match items across edits.
     */
    public ObjectListFieldBuilder itemKey(String fieldKey) {
        this.itemKey = Objects.requireNonNull(fieldKey, "fieldKey");
        return this;
    }

    /** Declares the scalar item field shown as each item's title. Defaults to the item key field. */
    public ObjectListFieldBuilder itemTitle(String fieldKey) {
        this.itemTitle = Objects.requireNonNull(fieldKey, "fieldKey");
        return this;
    }

    /** Singular noun for one item, such as "voucher", used in labels like "Add voucher". */
    public ObjectListFieldBuilder itemLabel(String itemLabel) {
        this.itemLabel = Objects.requireNonNull(itemLabel, "itemLabel");
        return this;
    }

    public ObjectListFieldBuilder minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    public ObjectListFieldBuilder maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    /** Sets whether item order is meaningful and editable. Defaults to true. */
    public ObjectListFieldBuilder ordered(boolean ordered) {
        this.ordered = ordered;
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
        checkItemRange();
        if (fields.isEmpty()) {
            throw fail("needs at least one item field");
        }
        if (itemLabel != null && (itemLabel.isBlank() || itemLabel.length() > 40)) {
            throw fail("itemLabel must be 1-40 characters");
        }
        SchemaChecks.checkSiblings(fields, "items of '" + key + "'");
        for (ConfigField field : fields) {
            SchemaChecks.checkNoSensitive(field, key);
        }
        if (itemKey != null) {
            ConfigField keyField = fields.stream().filter(f -> f.key().equals(itemKey)).findFirst()
                    .orElseThrow(() -> fail("itemKey '" + itemKey + "' is not an item field"));
            if (keyField.type() != FieldType.STRING || !keyField.required()) {
                throw fail("itemKey '" + itemKey + "' must be a required string field");
            }
        }
        if (itemTitle == null) {
            itemTitle = itemKey;
        }
        if (itemTitle != null) {
            ConfigField titleField = fields.stream().filter(f -> f.key().equals(itemTitle)).findFirst()
                    .orElseThrow(() -> fail("itemTitle '" + itemTitle + "' is not an item field"));
            if (!titleField.type().isScalar()) {
                throw fail("itemTitle '" + itemTitle + "' must be a scalar field");
            }
        }
    }

    @Override
    void validateDefault(Object value) {
        throw fail("object list fields have no default value");
    }
}
