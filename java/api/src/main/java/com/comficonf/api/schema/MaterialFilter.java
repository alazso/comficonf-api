package com.comficonf.api.schema;

/** Restricts which materials a {@link FieldType#MATERIAL} field accepts. */
public enum MaterialFilter {
    /** Any material that is a block or an item. */
    ANY("any"),
    /** Materials that are placeable blocks ({@code Material.isBlock()}). */
    BLOCK("block"),
    /** Materials that exist as items ({@code Material.isItem()}). */
    ITEM("item");

    private final String protocolName;

    MaterialFilter(String protocolName) {
        this.protocolName = protocolName;
    }

    /** Name used in the wire protocol. */
    public String protocolName() {
        return protocolName;
    }
}
