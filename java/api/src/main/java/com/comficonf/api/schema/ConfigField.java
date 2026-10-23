package com.comficonf.api.schema;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Immutable definition of one editable field. Created with the static factories of this class, which return a
 * type-specific builder:
 *
 * <pre>{@code
 * ConfigField.integer("cooldown-seconds")
 *         .label("Cooldown")
 *         .description("Time before a player can redeem this voucher again.")
 *         .range(0, 604_800)
 *         .unit("seconds")
 * }</pre>
 *
 * <p>Fields are required unless {@link FieldBuilder#optional()} is called. Attributes that do not apply to a
 * field's type are null, false or empty.
 */
public final class ConfigField {

    private final String key;
    private final FieldType type;
    private final String label;
    private final @Nullable String description;
    private final @Nullable String placeholder;
    private final boolean required;
    private final boolean readOnly;
    private final boolean sensitive;
    private final boolean writeOnly;
    private final ApplyMode applyMode;
    private final @Nullable VisibilityCondition visibleWhen;
    private final @Nullable Object defaultValue;
    private final @Nullable Integer minLength;
    private final @Nullable Integer maxLength;
    private final @Nullable String pattern;
    private final @Nullable String patternMessage;
    private final @Nullable Number min;
    private final @Nullable Number max;
    private final @Nullable Number step;
    private final @Nullable String unit;
    private final boolean slider;
    private final List<Choice> choices;
    private final @Nullable Integer minItems;
    private final @Nullable Integer maxItems;
    private final boolean uniqueItems;
    private final boolean ordered;
    private final @Nullable MaterialFilter materialFilter;
    private final List<ConfigField> fields;
    private final @Nullable String itemKey;
    private final @Nullable String itemTitle;
    private final @Nullable String itemLabel;

    ConfigField(FieldBuilder<?> builder, String label) {
        this.key = builder.key;
        this.type = builder.type;
        this.label = label;
        this.description = builder.description;
        this.placeholder = builder.placeholder;
        this.required = builder.required;
        this.readOnly = builder.readOnly;
        this.sensitive = builder.sensitive;
        this.writeOnly = builder.writeOnly;
        this.applyMode = builder.applyMode;
        this.visibleWhen = builder.visibleWhen;
        this.defaultValue = builder.defaultValue;
        this.minLength = builder.minLength;
        this.maxLength = builder.maxLength;
        this.pattern = builder.pattern;
        this.patternMessage = builder.patternMessage;
        this.min = builder.min;
        this.max = builder.max;
        this.step = builder.step;
        this.unit = builder.unit;
        this.slider = builder.slider;
        this.choices = List.copyOf(builder.choices);
        this.minItems = builder.minItems;
        this.maxItems = builder.maxItems;
        this.uniqueItems = builder.uniqueItems;
        this.ordered = builder.ordered;
        this.materialFilter = builder.materialFilter;
        this.fields = List.copyOf(builder.fields);
        this.itemKey = builder.itemKey;
        this.itemTitle = builder.itemTitle;
        this.itemLabel = builder.itemLabel;
    }

    /** Single-line text field. */
    public static StringFieldBuilder string(String key) {
        return new StringFieldBuilder(key, FieldType.STRING);
    }

    /** Multi-line text field. */
    public static StringFieldBuilder text(String key) {
        return new StringFieldBuilder(key, FieldType.TEXT);
    }

    /** Boolean field shown as a toggle. */
    public static BooleanFieldBuilder bool(String key) {
        return new BooleanFieldBuilder(key);
    }

    /** Whole-number field. */
    public static IntegerFieldBuilder integer(String key) {
        return new IntegerFieldBuilder(key);
    }

    /** Decimal-number field. */
    public static DecimalFieldBuilder decimal(String key) {
        return new DecimalFieldBuilder(key);
    }

    /** Field holding one of the declared choices. */
    public static EnumFieldBuilder enumeration(String key) {
        return new EnumFieldBuilder(key);
    }

    /** Field holding one constant name of {@code enumType}; every constant becomes a choice. */
    public static EnumFieldBuilder enumeration(String key, Class<? extends Enum<?>> enumType) {
        EnumFieldBuilder builder = new EnumFieldBuilder(key);
        for (Enum<?> constant : enumType.getEnumConstants()) {
            builder.choice(constant.name(), FieldBuilder.deriveLabel(constant.name()));
        }
        return builder;
    }

    /** List of strings, free-form or limited to declared choices. */
    public static StringListFieldBuilder stringList(String key) {
        return new StringListFieldBuilder(key);
    }

    /** Minecraft material name. */
    public static MaterialFieldBuilder material(String key) {
        return new MaterialFieldBuilder(key);
    }

    /** Group of nested fields. */
    public static ObjectFieldBuilder object(String key) {
        return new ObjectFieldBuilder(key);
    }

    /** List of objects with the same fields. */
    public static ObjectListFieldBuilder objectList(String key) {
        return new ObjectListFieldBuilder(key);
    }

    /** Key within the parent object: 1 to 64 letters, digits, {@code _} or {@code -}. */
    public String key() {
        return key;
    }

    public FieldType type() {
        return type;
    }

    public String label() {
        return label;
    }

    public @Nullable String description() {
        return description;
    }

    /** Placeholder for empty text inputs. */
    public @Nullable String placeholder() {
        return placeholder;
    }

    /**
     * Whether a value must be present. For {@code STRING}, {@code TEXT}, {@code ENUM} and {@code MATERIAL}
     * fields a required value must also be non-empty.
     */
    public boolean required() {
        return required;
    }

    /** Whether the editor shows the value without allowing changes. Applies to the whole subtree. */
    public boolean readOnly() {
        return readOnly;
    }

    /** Whether the value is secret. Sensitive values are never sent to the editor. */
    public boolean sensitive() {
        return sensitive;
    }

    /** Whether a sensitive value can be replaced from the editor without being revealed. */
    public boolean writeOnly() {
        return writeOnly;
    }

    public ApplyMode applyMode() {
        return applyMode;
    }

    public @Nullable VisibilityCondition visibleWhen() {
        return visibleWhen;
    }

    /** Default value suggested by the editor for new list items and empty optional values. */
    public @Nullable Object defaultValue() {
        return defaultValue;
    }

    /** Minimum length of {@code STRING} and {@code TEXT} values, or of each {@code STRING_LIST} item. */
    public @Nullable Integer minLength() {
        return minLength;
    }

    /** Maximum length of {@code STRING} and {@code TEXT} values, or of each {@code STRING_LIST} item. */
    public @Nullable Integer maxLength() {
        return maxLength;
    }

    /**
     * Regular expression the whole value must match, for {@code STRING}, {@code TEXT} and each
     * {@code STRING_LIST} item. Limited to syntax that Java and JavaScript interpret the same way.
     */
    public @Nullable String pattern() {
        return pattern;
    }

    /** Message shown when {@link #pattern()} does not match. */
    public @Nullable String patternMessage() {
        return patternMessage;
    }

    /** Inclusive minimum: a {@link Long} for {@code INTEGER}, a {@link Double} for {@code DECIMAL}. */
    public @Nullable Number min() {
        return min;
    }

    /** Inclusive maximum: a {@link Long} for {@code INTEGER}, a {@link Double} for {@code DECIMAL}. */
    public @Nullable Number max() {
        return max;
    }

    /** Increment used by the editor's number controls. A hint; values are not required to be multiples. */
    public @Nullable Number step() {
        return step;
    }

    /** Unit shown next to number inputs, such as {@code seconds}. */
    public @Nullable String unit() {
        return unit;
    }

    /** Whether the editor shows a slider next to the number input. Requires {@link #min()} and {@link #max()}. */
    public boolean slider() {
        return slider;
    }

    /** Allowed values of {@code ENUM} fields, or of {@code STRING_LIST} items when not empty. */
    public List<Choice> choices() {
        return choices;
    }

    /** Minimum number of items of list fields. */
    public @Nullable Integer minItems() {
        return minItems;
    }

    /** Maximum number of items of list fields. */
    public @Nullable Integer maxItems() {
        return maxItems;
    }

    /** Whether {@code STRING_LIST} items must be distinct. */
    public boolean uniqueItems() {
        return uniqueItems;
    }

    /** Whether the order of list items is meaningful and can be changed in the editor. */
    public boolean ordered() {
        return ordered;
    }

    /** Accepted materials of {@code MATERIAL} fields. */
    public @Nullable MaterialFilter materialFilter() {
        return materialFilter;
    }

    /** Child fields of {@code OBJECT} fields, or item fields of {@code OBJECT_LIST} fields. */
    public List<ConfigField> fields() {
        return fields;
    }

    /** Returns the child or item field with {@code key}. */
    public Optional<ConfigField> field(String key) {
        return fields.stream().filter(field -> field.key.equals(key)).findFirst();
    }

    /** Key of the item field whose value identifies {@code OBJECT_LIST} items; values must be unique. */
    public @Nullable String itemKey() {
        return itemKey;
    }

    /** Key of the item field shown as the title of {@code OBJECT_LIST} items. */
    public @Nullable String itemTitle() {
        return itemTitle;
    }

    /** Singular noun for one {@code OBJECT_LIST} item, used in labels such as "Add voucher". */
    public @Nullable String itemLabel() {
        return itemLabel;
    }

    /** Nesting depth of this field: 1 for fields without children. */
    public int depth() {
        int deepest = 0;
        for (ConfigField child : fields) {
            deepest = Math.max(deepest, child.depth());
        }
        return deepest + 1;
    }

    /** Number of fields in this field's subtree, including itself. */
    public int fieldCount() {
        int count = 1;
        for (ConfigField child : fields) {
            count += child.fieldCount();
        }
        return count;
    }

    @Override
    public String toString() {
        return "ConfigField[" + key + ": " + type.protocolName() + "]";
    }
}
