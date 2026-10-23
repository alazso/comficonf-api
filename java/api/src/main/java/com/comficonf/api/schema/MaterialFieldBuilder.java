package com.comficonf.api.schema;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Builder of {@link FieldType#MATERIAL} fields. Values are material names as returned by
 * {@code Material.name()}. The bridge accepts only materials that exist on the running server version and
 * match the {@link MaterialFilter}.
 */
public final class MaterialFieldBuilder extends FieldBuilder<MaterialFieldBuilder> {

    MaterialFieldBuilder(String key) {
        super(key, FieldType.MATERIAL);
        this.materialFilter = MaterialFilter.ANY;
    }

    @Override
    MaterialFieldBuilder self() {
        return this;
    }

    /** Restricts accepted materials. Defaults to {@link MaterialFilter#ANY}. */
    public MaterialFieldBuilder filter(MaterialFilter filter) {
        this.materialFilter = Objects.requireNonNull(filter, "filter");
        return this;
    }

    /** Accepts only materials that exist as items. */
    public MaterialFieldBuilder itemsOnly() {
        return filter(MaterialFilter.ITEM);
    }

    /** Accepts only placeable blocks. */
    public MaterialFieldBuilder blocksOnly() {
        return filter(MaterialFilter.BLOCK);
    }

    public MaterialFieldBuilder defaultValue(@Nullable String value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    void validateType() {
        checkNoNumericAttributes();
    }

    @Override
    void validateDefault(Object value) {
        if (!(value instanceof String text) || text.isEmpty()
                || !text.equals(text.toUpperCase(Locale.ROOT))) {
            throw fail("default value must be an upper-case material name such as DIAMOND");
        }
    }
}
