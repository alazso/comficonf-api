package com.comficonf.api.schema;

/** Type of a {@link ConfigField}, which determines its value type and its control in the web editor. */
public enum FieldType {
    /** Single-line text. Value: {@link String}. Control: text input. */
    STRING("string"),
    /** Multi-line text. Value: {@link String}. Control: text area. */
    TEXT("text"),
    /** Value: {@link Boolean}. Control: toggle. */
    BOOLEAN("boolean"),
    /** Whole number within ±(2<sup>53</sup> - 1). Value: {@link Long}. Control: number input. */
    INTEGER("integer"),
    /** Decimal number. Value: {@link Double}. Control: decimal input. */
    DECIMAL("decimal"),
    /** One value out of declared choices. Value: {@link String}. Control: dropdown. */
    ENUM("enum"),
    /**
     * List of strings. Value: {@link java.util.List} of {@link String}. Control: multi-select when choices are
     * declared, otherwise a tag input.
     */
    STRING_LIST("stringList"),
    /**
     * Minecraft material name as returned by {@code Material.name()}, for example {@code DIAMOND_SWORD}.
     * Value: {@link String}. Control: searchable material picker filled from the connected server.
     */
    MATERIAL("material"),
    /** Group of nested fields. Value: {@link java.util.Map}. Control: grouped section. */
    OBJECT("object"),
    /**
     * List of objects sharing the same fields. Value: {@link java.util.List} of {@link java.util.Map}.
     * Control: repeatable cards with add, remove, duplicate, reorder and bulk edit.
     */
    OBJECT_LIST("objectList");

    private final String protocolName;

    FieldType(String protocolName) {
        this.protocolName = protocolName;
    }

    /** Name used in the wire protocol. */
    public String protocolName() {
        return protocolName;
    }

    /** Whether values of this type are single values rather than containers or lists. */
    public boolean isScalar() {
        return this != STRING_LIST && this != OBJECT && this != OBJECT_LIST;
    }
}
