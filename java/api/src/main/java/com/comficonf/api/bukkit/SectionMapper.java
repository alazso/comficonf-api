package com.comficonf.api.bukkit;

import com.comficonf.api.ConfigPath;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.ValidationResult;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;
import com.comficonf.api.schema.FieldType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Maps a Bukkit {@link ConfigurationSection}, such as a {@code YamlConfiguration} parsed from a plugin's
 * {@code config.yml}, to the {@link ConfigValues} of a {@link ConfigSchema} and back.
 *
 * <pre>{@code
 * SectionMapper mapper = SectionMapper.builder(schema).keyedSection("vouchers").build();
 *
 * ConfigValues values = mapper.read(yaml);       // in ConfigProvider.read()
 * mapper.write(context.proposed(), yaml);        // in ConfigProvider.apply(), before saving yaml
 * }</pre>
 *
 * <p>The schema mirrors the layout of the section: each field is stored under its key, an {@code object} field is a
 * nested section, and an {@code objectList} field is a list of sections, or a section of sections keyed by the
 * item key when declared with {@link Builder#keyedSection}. Only declared fields are read and written. Keys the
 * schema does not declare, at any depth, keep their values, and keys that stay keep the comments the section
 * holds for them. Defaults of the section's root configuration are ignored: only stored values are read.
 *
 * <h2>Reading</h2>
 *
 * <ul>
 *   <li>{@code string}, {@code text}, {@code enum}, {@code material}: a string as stored; a number or boolean as
 *       its text, as {@link ConfigurationSection#getString} returns it, so an unquoted YAML {@code 123} or
 *       {@code yes} in a text field (for example a numeric secret) reads as {@code "123"} or {@code "true"}. For an
 *       optional field, an empty string reads as {@code null}.</li>
 *   <li>{@code boolean}: a boolean.</li>
 *   <li>{@code integer}: a whole number as {@link Long}, and a decimal number without a fractional part within
 *       ±(2<sup>53</sup> - 1) as {@link Long}.</li>
 *   <li>{@code decimal}: any number as {@link Double}.</li>
 *   <li>{@code stringList}: a list; strings as stored, numbers and booleans as their text, {@code null} items
 *       skipped.</li>
 *   <li>{@code object}: a section whose children are read by the same rules. A missing section reads as an object
 *       whose fields are all missing.</li>
 *   <li>{@code objectList}: a list of sections, in order, {@code null} items skipped. Declared with
 *       {@link Builder#keyedSection}: a section whose keys are the item keys, in file order, each mapping to the
 *       item's section; the key becomes the value of the {@code itemKey} field. An empty list ({@code []}) reads
 *       as no items.</li>
 *   <li>A missing key reads as {@code null}, except that a missing required {@code stringList} or
 *       {@code objectList} reads as an empty list, as {@link ConfigurationSection#getStringList} does.</li>
 *   <li>A single value of another kind (text in a boolean or number field, a decimal number with a fraction in an
 *       integer field, a single value where a string list belongs) is read unchanged, normalized as
 *       {@link ConfigValues} normalizes it, so the editor reports it at its path and the server owner can correct
 *       it there.</li>
 * </ul>
 *
 * <h2>Writing</h2>
 *
 * <ul>
 *   <li>The values are complete, as {@code ApplyContext.proposed()} is: a declared field without a value is
 *       written as no value.</li>
 *   <li>A field whose stored value already reads as the value to write is left as it is, so its stored form stays
 *       (a decimal field stored as {@code 2} keeps {@code 2}, a text field stored as {@code 123} keeps
 *       {@code 123}).</li>
 *   <li>Strings, booleans and decimals are stored as they are, integers as {@link Integer} when they fit in an
 *       {@code int} and as {@link Long} otherwise (the types YAML loads), and string lists as lists.</li>
 *   <li>An {@code object} field is written into its nested section, which is created only when one of its values
 *       is written.</li>
 *   <li>An {@code objectList} field is written as a list in the given order. Each item starts from the stored item
 *       with the same {@code itemKey} value, or at the same position when the field has no {@code itemKey}, so the
 *       item's undeclared keys stay.</li>
 *   <li>An {@code objectList} declared with {@link Builder#keyedSection} is written as one section per item, in
 *       the given order, named by the item's key. The stored section of the same key is reused with its undeclared
 *       keys and comments, and the {@code itemKey} field is not written inside it. Sections of items that are no
 *       longer present are removed.</li>
 *   <li>No value ({@code null}): an optional {@code string}, {@code text}, {@code enum} or {@code material} field
 *       is stored as an empty string, so its key and comments stay; any other field's key is removed.</li>
 * </ul>
 *
 * <p>Writing the values that {@link #read} returned leaves the section's values unchanged.
 *
 * <p>Sensitive values are read and written like any other value. {@link #read} returns them so that the bridge can
 * report whether one is set without sending it anywhere, and {@link #write} stores the values of
 * {@code ApplyContext.proposed()}, into which the bridge merged the stored secrets that the editor did not
 * replace. Exception messages name paths and types, never values.
 *
 * <p>Instances are immutable and thread-safe. A {@link ConfigurationSection} is not thread-safe: use one section
 * from one thread at a time.
 */
public final class SectionMapper {

    private static final double MAX_SAFE_INTEGER = 9_007_199_254_740_991d;
    private static final String UNSTORABLE_TEXT = "Contains a control character, such as a pasted terminal escape"
            + " code, that a YAML file cannot store as text.";
    private static final String KEY_WITH_SEPARATOR = "Cannot contain '.', because it is the key of this item in"
            + " the configuration file.";
    private static final Object UNREADABLE = new Object();

    private final ConfigSchema schema;
    private final Set<String> keyedSections;

    private SectionMapper(ConfigSchema schema, Set<String> keyedSections) {
        this.schema = schema;
        this.keyedSections = keyedSections;
    }

    /** Returns a mapper that stores every {@code objectList} field as a list of sections. */
    public static SectionMapper of(ConfigSchema schema) {
        return builder(schema).build();
    }

    /** Starts a builder for {@code schema}. */
    public static Builder builder(ConfigSchema schema) {
        return new Builder(Objects.requireNonNull(schema, "schema"));
    }

    public ConfigSchema schema() {
        return schema;
    }

    /**
     * Reads the declared values from {@code section}, by the rules in the class description.
     *
     * @throws IllegalStateException naming the path, if a stored value cannot be read without losing data: a
     *     section or list where a single value belongs, a single value or list where a section belongs, a section
     *     or single value where a list belongs, a list item of the wrong kind, binary data ({@code !!binary}, which
     *     YAML writes for text containing control characters), a number that is not finite or does not fit in a
     *     {@code long}, or a value of another type such as a date or a serialized object
     * @throws IllegalArgumentException if a field key contains the path separator of the section's root
     *     configuration
     */
    public ConfigValues read(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        checkSeparator(section);
        return ConfigValues.of(readObject(schema.fields(), section, section.getCurrentPath(), ""));
    }

    /**
     * Writes {@code values} into {@code section}, by the rules in the class description. Saving the section, for
     * example with {@code YamlConfiguration.saveToString()}, is up to the caller.
     *
     * <p>When this method throws, the section may already be partly written; parse the file again before using it.
     *
     * @throws IllegalArgumentException if an item key of a {@link Builder#keyedSection} list is missing, empty,
     *     repeated or contains the path separator of the section's root configuration, if the value of an
     *     {@code object} field is not an object or the value of an {@code objectList} field is not a list of
     *     objects, or if a field key contains that path separator
     */
    public void write(ConfigValues values, ConfigurationSection section) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(section, "section");
        char separator = checkSeparator(section);
        writeObject(schema.fields(), values.asMap(), new SectionTarget(section), "", "", separator);
    }

    /**
     * Reports, as errors at their paths, the values that a YAML file saved by Bukkit's {@code YamlConfiguration}
     * cannot store unchanged:
     *
     * <ul>
     *   <li>text of {@code string}, {@code text}, {@code enum} and {@code material} fields and {@code stringList}
     *       items that contains a code point other than tab, line feed, carriage return, U+0020 to U+007E, U+00A0
     *       to U+D7FF, U+E000 to U+FFFD and U+10000 and above. YAML saves such text (for example an escape code
     *       pasted from a terminal, U+001B) as {@code !!binary} data, which reads back as a {@code byte[]}, and
     *       reads U+0085 back as a line feed;</li>
     *   <li>item keys of {@link Builder#keyedSection} lists that contain {@code .}, the default path
     *       separator.</li>
     * </ul>
     *
     * <p>Call it from {@code ConfigProvider.validate} and merge your own rules with
     * {@link ValidationResult#merge}. It does not repeat the bridge's structural validation, which has already
     * checked types and constraints.
     */
    public ValidationResult validate(ConfigValues values) {
        Objects.requireNonNull(values, "values");
        ValidationResult.Builder result = ValidationResult.builder();
        checkObject(schema.fields(), values.asMap(), "", "", result);
        return result.build();
    }

    private Map<String, @Nullable Object> readObject(List<ConfigField> fields, @Nullable Object container,
            String path, String fieldPath) {
        Map<String, @Nullable Object> result = new LinkedHashMap<>();
        for (ConfigField field : fields) {
            Object stored = container == null ? null : stored(container, field.key());
            result.put(field.key(), readField(field, stored, filePath(path, field.key()),
                    schemaPath(fieldPath, field.key())));
        }
        return result;
    }

    private @Nullable Object readField(ConfigField field, @Nullable Object stored, String path, String fieldPath) {
        return switch (field.type()) {
            case STRING, TEXT, ENUM, MATERIAL -> readText(field, stored, path);
            case BOOLEAN -> single(stored, path, "a single value");
            case INTEGER -> readInteger(stored, path);
            case DECIMAL -> stored instanceof Number number
                    ? finite(number.doubleValue(), path)
                    : single(stored, path, "a single value");
            case STRING_LIST -> readStringList(field, stored, path);
            case OBJECT -> readObject(field.fields(), requireObject(stored, path), path, fieldPath);
            case OBJECT_LIST -> readObjectList(field, stored, path, fieldPath);
        };
    }

    private static @Nullable Object readText(ConfigField field, @Nullable Object stored, String path) {
        Object value = switch (stored) {
            case null -> null;
            case Boolean flag -> flag.toString();
            case Number number -> String.valueOf(number);
            default -> single(stored, path, "text");
        };
        return "".equals(value) && !field.required() ? null : value;
    }

    private static @Nullable Object readInteger(@Nullable Object stored, String path) {
        if (stored instanceof Double || stored instanceof Float) {
            double number = ((Number) stored).doubleValue();
            if (number == Math.rint(number) && Math.abs(number) <= MAX_SAFE_INTEGER) {
                return (long) number;
            }
        }
        return single(stored, path, "a single value");
    }

    private static @Nullable Object readStringList(ConfigField field, @Nullable Object stored, String path) {
        if (stored == null) {
            return field.required() ? List.of() : null;
        }
        if (!(stored instanceof List<?> list)) {
            if (isObject(stored)) {
                throw unreadable(stored, path, "a list");
            }
            return single(stored, path, "a list");
        }
        List<String> items = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            switch (item) {
                case null -> {
                }
                case String text -> items.add(text);
                case Boolean _, Number _ -> items.add(String.valueOf(item));
                default -> throw unreadable(item, path + "[" + i + "]", "text");
            }
        }
        return items;
    }

    private @Nullable Object readObjectList(ConfigField field, @Nullable Object stored, String path,
            String fieldPath) {
        if (stored == null) {
            return field.required() ? List.of() : null;
        }
        List<Map<String, @Nullable Object>> items = new ArrayList<>();
        String itemKey = field.itemKey();
        if (keyedSections.contains(fieldPath)) {
            if (stored instanceof List<?> list && list.isEmpty()) {
                return items;
            }
            if (!isObject(stored)) {
                throw unreadable(stored, path, "a section of items keyed by " + itemKey);
            }
            for (Map.Entry<String, Object> entry : entries(stored).entrySet()) {
                String itemPath = filePath(path, entry.getKey());
                Object container = requireObject(entry.getValue(), itemPath);
                Map<String, @Nullable Object> item = new LinkedHashMap<>();
                for (ConfigField child : field.fields()) {
                    if (child.key().equals(itemKey)) {
                        item.put(child.key(), entry.getKey());
                    } else {
                        Object value = container == null ? null : stored(container, child.key());
                        item.put(child.key(), readField(child, value, filePath(itemPath, child.key()),
                                schemaPath(fieldPath, child.key())));
                    }
                }
                items.add(item);
            }
            return items;
        }
        if (!(stored instanceof List<?> list)) {
            IllegalStateException problem = unreadable(stored, path, "a list");
            if (isObject(stored) && itemKey != null) {
                problem = new IllegalStateException(problem.getMessage() + "; to read a section keyed by "
                        + itemKey + ", declare it with SectionMapper.Builder.keyedSection(\"" + fieldPath + "\")");
            }
            throw problem;
        }
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item == null) {
                continue;
            }
            String itemPath = path + "[" + i + "]";
            if (!isObject(item)) {
                throw unreadable(item, itemPath, "a section");
            }
            items.add(readObject(field.fields(), item, itemPath, fieldPath));
        }
        return items;
    }

    /** Returns {@code stored} if it is a section or null; throws for anything else. */
    private static @Nullable Object requireObject(@Nullable Object stored, String path) {
        if (stored == null || isObject(stored)) {
            return stored;
        }
        throw unreadable(stored, path, "a section");
    }

    /** Normalizes a single value as {@link ConfigValues} does, or throws for anything else. */
    private static @Nullable Object single(@Nullable Object stored, String path, String expected) {
        return switch (stored) {
            case null -> null;
            case String text -> text;
            case Boolean flag -> flag;
            case Integer number -> number.longValue();
            case Long number -> number;
            case Short number -> number.longValue();
            case Byte number -> number.longValue();
            case BigInteger number -> {
                if (number.bitLength() > 63) {
                    throw new IllegalStateException(path + " is a whole number that does not fit in a long");
                }
                yield number.longValue();
            }
            case Number number -> finite(number.doubleValue(), path);
            default -> throw unreadable(stored, path, expected);
        };
    }

    private static Double finite(double number, String path) {
        if (!Double.isFinite(number)) {
            throw new IllegalStateException(path + " is not a finite number");
        }
        return number;
    }

    private static IllegalStateException unreadable(Object stored, String path, String expected) {
        String message = switch (stored) {
            case byte[] _ -> path + " is stored as binary data (!!binary) instead of text; replace it with text in"
                    + " the file";
            case ConfigurationSection _, Map<?, ?> _ -> path + " is a section, not " + expected;
            case List<?> _ -> path + " is a list, not " + expected;
            case String _, Boolean _, Number _ -> path + " is a single value, not " + expected;
            default -> path + " holds a value of type " + stored.getClass().getSimpleName()
                    + ", which cannot be read as " + expected;
        };
        return new IllegalStateException(message);
    }

    private void writeObject(List<ConfigField> fields, Map<?, ?> values, Target target, String path,
            String fieldPath, char separator) {
        for (ConfigField field : fields) {
            writeField(field, values.get(field.key()), target, ConfigPath.child(path, field.key()),
                    schemaPath(fieldPath, field.key()), separator);
        }
    }

    private void writeField(ConfigField field, @Nullable Object value, Target target, String path,
            String fieldPath, char separator) {
        String key = field.key();
        switch (field.type()) {
            case OBJECT -> writeObject(field.fields(), object(value, path), target.child(key, false), path,
                    fieldPath, separator);
            case OBJECT_LIST -> writeObjectList(field, value, target, path, fieldPath, separator);
            default -> {
                if (Objects.equals(readLeniently(field, target.get(key), path, fieldPath), value)) {
                    return;
                }
                if (value == null) {
                    target.set(key, isText(field) && !field.required() ? "" : null);
                } else {
                    target.set(key, toStored(value));
                }
            }
        }
    }

    private void writeObjectList(ConfigField field, @Nullable Object value, Target target, String path,
            String fieldPath, char separator) {
        String key = field.key();
        Object stored = target.get(key);
        if (Objects.equals(readLeniently(field, stored, path, fieldPath), value)) {
            return;
        }
        if (value == null) {
            target.set(key, null);
            return;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(path + " must be a list of objects");
        }
        List<Map<?, ?>> items = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            items.add(object(list.get(i), ConfigPath.index(path, i)));
        }
        if (keyedSections.contains(fieldPath)) {
            writeKeyed(field, items, target.child(key, true), path, fieldPath, separator);
        } else {
            writeList(field, items, stored, target, path, fieldPath, separator);
        }
    }

    private void writeList(ConfigField field, List<Map<?, ?>> items, @Nullable Object stored, Target target,
            String path, String fieldPath, char separator) {
        List<Object> storedItems = new ArrayList<>();
        if (stored instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    storedItems.add(item);
                }
            }
        }
        String itemKey = field.itemKey();
        Map<String, Object> storedByKey = new HashMap<>();
        if (itemKey != null) {
            for (Object item : storedItems) {
                Object id = isObject(item) ? stored(item, itemKey) : null;
                if (id != null) {
                    storedByKey.putIfAbsent(String.valueOf(id), item);
                }
            }
        }
        List<Object> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Map<?, ?> item = items.get(i);
            Object match;
            if (itemKey != null) {
                Object id = item.get(itemKey);
                match = id == null ? null : storedByKey.get(String.valueOf(id));
            } else {
                match = i < storedItems.size() ? storedItems.get(i) : null;
            }
            Map<Object, @Nullable Object> copy = copy(match);
            writeObject(field.fields(), item, new MapTarget(copy), ConfigPath.index(path, i), fieldPath, separator);
            result.add(copy);
        }
        if (!result.equals(stored)) {
            target.set(field.key(), result);
        }
    }

    private void writeKeyed(ConfigField field, List<Map<?, ?>> items, Target container, String path,
            String fieldPath, char separator) {
        String itemKey = Objects.requireNonNull(field.itemKey(), "itemKey");
        List<String> keys = new ArrayList<>(items.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String keyPath = ConfigPath.child(ConfigPath.index(path, i), itemKey);
            if (!(items.get(i).get(itemKey) instanceof String key) || key.isEmpty()) {
                throw new IllegalArgumentException(keyPath + " must be a non-empty string, the key of the item's"
                        + " section");
            }
            if (key.indexOf(separator) >= 0) {
                throw new IllegalArgumentException(keyPath + " contains the path separator '" + separator + "'");
            }
            if (!seen.add(key)) {
                throw new IllegalArgumentException(keyPath + " repeats the key of an earlier item");
            }
            keys.add(key);
        }
        for (String stored : container.keys()) {
            if (!seen.contains(stored)) {
                container.set(stored, null);
            }
        }
        List<ConfigField> fields = field.fields().stream().filter(child -> !child.key().equals(itemKey)).toList();
        for (int i = 0; i < items.size(); i++) {
            writeObject(fields, items.get(i), container.child(keys.get(i), true), ConfigPath.index(path, i),
                    fieldPath, separator);
        }
        container.order(keys);
    }

    private @Nullable Object readLeniently(ConfigField field, @Nullable Object stored, String path,
            String fieldPath) {
        try {
            return readField(field, stored, path, fieldPath);
        } catch (IllegalStateException e) {
            return UNREADABLE;
        }
    }

    private static Map<?, ?> object(@Nullable Object value, String path) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException(path + " must be an object");
    }

    private static Object toStored(Object value) {
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    private static boolean isText(ConfigField field) {
        FieldType type = field.type();
        return type == FieldType.STRING || type == FieldType.TEXT || type == FieldType.ENUM
                || type == FieldType.MATERIAL;
    }

    private void checkObject(List<ConfigField> fields, Map<?, ?> values, String path, String fieldPath,
            ValidationResult.Builder result) {
        for (ConfigField field : fields) {
            String childPath = ConfigPath.child(path, field.key());
            String childFieldPath = schemaPath(fieldPath, field.key());
            Object value = values.get(field.key());
            switch (field.type()) {
                case STRING, TEXT, ENUM, MATERIAL -> {
                    if (value instanceof String text && !storesAsText(text)) {
                        result.error(childPath, UNSTORABLE_TEXT);
                    }
                }
                case STRING_LIST -> {
                    if (value instanceof List<?> list) {
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i) instanceof String text && !storesAsText(text)) {
                                result.error(ConfigPath.index(childPath, i), UNSTORABLE_TEXT);
                            }
                        }
                    }
                }
                case OBJECT -> {
                    if (value instanceof Map<?, ?> map) {
                        checkObject(field.fields(), map, childPath, childFieldPath, result);
                    }
                }
                case OBJECT_LIST -> {
                    if (value instanceof List<?> list) {
                        String itemKey = field.itemKey();
                        boolean keyed = keyedSections.contains(childFieldPath);
                        for (int i = 0; i < list.size(); i++) {
                            if (!(list.get(i) instanceof Map<?, ?> item)) {
                                continue;
                            }
                            String itemPath = ConfigPath.index(childPath, i);
                            checkObject(field.fields(), item, itemPath, childFieldPath, result);
                            if (keyed && itemKey != null && item.get(itemKey) instanceof String key
                                    && key.indexOf('.') >= 0) {
                                result.error(ConfigPath.child(itemPath, itemKey), KEY_WITH_SEPARATOR);
                            }
                        }
                    }
                }
                case BOOLEAN, INTEGER, DECIMAL -> {
                }
            }
        }
    }

    /**
     * Whether Bukkit's {@code YamlConfiguration} saves {@code text} as a YAML string that reads back unchanged.
     * SnakeYAML saves a string containing other code points (control characters such as ESC and DEL, C1 controls,
     * U+FFFE and U+FFFF) as {@code !!binary} data, cannot save a string with an unpaired surrogate at all, and
     * reads U+0085 (next line) back as a line feed.
     */
    private static boolean storesAsText(String text) {
        return text.codePoints().allMatch(c -> c == 0x9 || c == 0xA || c == 0xD || c >= 0x20 && c <= 0x7E
                || c >= 0xA0 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF);
    }

    /** Returns the separator of the section's root configuration; throws if a field key contains it. */
    private char checkSeparator(ConfigurationSection section) {
        Configuration root = section.getRoot();
        char separator = root == null ? '.' : root.options().pathSeparator();
        if (separator != '.') {
            String key = keyContaining(schema.fields(), separator);
            if (key != null) {
                throw new IllegalArgumentException("Field key '" + key + "' contains the path separator '"
                        + separator + "' of the configuration");
            }
        }
        return separator;
    }

    private static @Nullable String keyContaining(List<ConfigField> fields, char separator) {
        for (ConfigField field : fields) {
            if (field.key().indexOf(separator) >= 0) {
                return field.key();
            }
            String nested = keyContaining(field.fields(), separator);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean isObject(@Nullable Object value) {
        return value instanceof ConfigurationSection || value instanceof Map<?, ?>;
    }

    /** Returns the value stored under {@code key} in a section or map, ignoring defaults. */
    private static @Nullable Object stored(Object container, String key) {
        if (container instanceof ConfigurationSection section) {
            return section.get(key, null);
        }
        Map<?, ?> map = (Map<?, ?>) container;
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (key.equals(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns the stored entries of a section or map with their keys as text, in order, without null values. */
    private static Map<String, Object> entries(Object container) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (container instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key, null);
                if (value != null) {
                    result.put(key, value);
                }
            }
        } else {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) container).entrySet()) {
                if (entry.getValue() != null) {
                    result.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return result;
    }

    private static Map<Object, @Nullable Object> copy(@Nullable Object container) {
        Map<Object, @Nullable Object> copy = new LinkedHashMap<>();
        if (container instanceof ConfigurationSection section) {
            copy.putAll(section.getValues(false));
        } else if (container instanceof Map<?, ?> map) {
            copy.putAll(map);
        }
        return copy;
    }

    private static String filePath(String parent, String key) {
        return parent.isEmpty() ? key : parent + "." + key;
    }

    private static String schemaPath(String parent, String key) {
        return parent.isEmpty() ? key : parent + "." + key;
    }

    /** A section or map that values are written into. */
    private abstract static sealed class Target permits SectionTarget, MapTarget, LazyTarget {

        abstract @Nullable Object get(String key);

        /** Stores {@code value} under {@code key}; {@code null} removes the key. */
        abstract void set(String key, @Nullable Object value);

        /** Keys with a stored value, in order. */
        abstract List<String> keys();

        /**
         * Returns the object stored under {@code key}. When none is stored, it is created now if {@code create} is
         * true, and otherwise on the first write into it.
         */
        abstract Target child(String key, boolean create);

        /** Moves the entries of {@code keys} to the front, in that order. */
        abstract void order(List<String> keys);
    }

    private static final class SectionTarget extends Target {
        private final ConfigurationSection section;

        SectionTarget(ConfigurationSection section) {
            this.section = section;
        }

        @Override
        @Nullable Object get(String key) {
            return section.get(key, null);
        }

        @Override
        void set(String key, @Nullable Object value) {
            section.set(key, value);
        }

        @Override
        List<String> keys() {
            return List.copyOf(entries(section).keySet());
        }

        @Override
        Target child(String key, boolean create) {
            Object stored = section.get(key, null);
            if (stored instanceof ConfigurationSection nested) {
                return new SectionTarget(nested);
            }
            if (create) {
                return replace(key, stored);
            }
            return new LazyTarget(() -> replace(key, stored), isObject(stored) ? stored : null);
        }

        /**
         * Replaces the value under {@code key} with a new section, which holds the entries of the stored value if
         * that is a map. The key keeps its comments.
         */
        private Target replace(String key, @Nullable Object stored) {
            List<String> comments = section.getComments(key);
            List<String> inlineComments = section.getInlineComments(key);
            ConfigurationSection created = stored instanceof Map<?, ?> map
                    ? section.createSection(key, map)
                    : section.createSection(key);
            section.setComments(key, comments);
            section.setInlineComments(key, inlineComments);
            return new SectionTarget(created);
        }

        /**
         * Removes and adds the keys again in the new order. A key keeps its value, including a nested section with
         * its own content, and its comments.
         */
        @Override
        void order(List<String> keys) {
            List<String> current = keys();
            if (current.equals(keys)) {
                return;
            }
            record Entry(Object value, List<String> comments, List<String> inlineComments) {
            }
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (String key : current) {
                entries.put(key, new Entry(Objects.requireNonNull(section.get(key, null)), section.getComments(key),
                        section.getInlineComments(key)));
                section.set(key, null);
            }
            Set<String> order = new LinkedHashSet<>(keys);
            order.addAll(current);
            for (String key : order) {
                Entry entry = entries.get(key);
                if (entry != null) {
                    section.set(key, entry.value());
                    section.setComments(key, entry.comments());
                    section.setInlineComments(key, entry.inlineComments());
                }
            }
        }
    }

    private static final class MapTarget extends Target {
        private final Map<Object, @Nullable Object> map;

        MapTarget(Map<Object, @Nullable Object> map) {
            this.map = map;
        }

        @Override
        @Nullable Object get(String key) {
            return stored(map, key);
        }

        /** Also removes keys of other types with the same text, such as the YAML integer key {@code 1}. */
        @Override
        void set(String key, @Nullable Object value) {
            map.keySet().removeIf(existing -> !key.equals(existing) && key.equals(String.valueOf(existing)));
            if (value == null) {
                map.remove(key);
            } else {
                map.put(key, value);
            }
        }

        @Override
        List<String> keys() {
            return List.copyOf(entries(map).keySet());
        }

        /** Stores a copy of a stored object before returning it, so that the stored object is never modified. */
        @Override
        Target child(String key, boolean create) {
            Object stored = get(key);
            if (isObject(stored) || create) {
                Map<Object, @Nullable Object> copy = copy(stored);
                set(key, copy);
                return new MapTarget(copy);
            }
            return new LazyTarget(() -> child(key, true), null);
        }

        @Override
        void order(List<String> keys) {
            Map<Object, @Nullable Object> previous = new LinkedHashMap<>(map);
            map.clear();
            for (String key : keys) {
                if (previous.containsKey(key)) {
                    map.put(key, previous.remove(key));
                }
            }
            map.putAll(previous);
        }
    }

    /** An object that is created on the first write; until then it reads from the stored object, if any. */
    private static final class LazyTarget extends Target {
        private final Supplier<Target> creator;
        private final @Nullable Object stored;
        private @Nullable Target created;

        LazyTarget(Supplier<Target> creator, @Nullable Object stored) {
            this.creator = creator;
            this.stored = stored;
        }

        private Target target() {
            Target target = created;
            if (target == null) {
                target = creator.get();
                created = target;
            }
            return target;
        }

        @Override
        @Nullable Object get(String key) {
            if (created != null) {
                return created.get(key);
            }
            return stored == null ? null : stored(stored, key);
        }

        @Override
        void set(String key, @Nullable Object value) {
            target().set(key, value);
        }

        @Override
        List<String> keys() {
            if (created != null) {
                return created.keys();
            }
            return stored == null ? List.of() : List.copyOf(entries(stored).keySet());
        }

        @Override
        Target child(String key, boolean create) {
            if (created != null || create) {
                return target().child(key, create);
            }
            Object nested = stored == null ? null : stored(stored, key);
            return new LazyTarget(() -> target().child(key, true), isObject(nested) ? nested : null);
        }

        @Override
        void order(List<String> keys) {
            target().order(keys);
        }
    }

    /** Builder of {@link SectionMapper}. Not thread-safe. */
    public static final class Builder {
        private final ConfigSchema schema;
        private final Set<String> keyedSections = new LinkedHashSet<>();

        private Builder(ConfigSchema schema) {
            this.schema = schema;
        }

        /**
         * Stores the {@code objectList} field at {@code fieldPath} as a section that maps each item's key (the value
         * of its {@code itemKey} field) to a section with the item's other fields, instead of as a list:
         *
         * <pre>{@code
         * vouchers:          # ConfigField.objectList("vouchers").itemKey("id")
         *   daily:           # item with id "daily"
         *     cooldown: 60
         * }</pre>
         *
         * <p>{@code fieldPath} joins the keys of the fields from the root to the list with {@code .}, without list
         * indexes: {@code vouchers}, {@code shop.categories}, or {@code kits.items} for a list inside the items of
         * the {@code kits} list.
         *
         * @throws IllegalArgumentException if no field has that path, or it is not an {@code objectList} field with
         *     an {@code itemKey}
         */
        public Builder keyedSection(String fieldPath) {
            Objects.requireNonNull(fieldPath, "fieldPath");
            List<ConfigField> fields = schema.fields();
            ConfigField field = null;
            for (String key : fieldPath.split("\\.", -1)) {
                if (field != null) {
                    fields = field.fields();
                }
                field = null;
                for (ConfigField candidate : fields) {
                    if (candidate.key().equals(key)) {
                        field = candidate;
                        break;
                    }
                }
                if (field == null) {
                    throw new IllegalArgumentException("The schema has no field '" + fieldPath + "'");
                }
            }
            if (field == null || field.type() != FieldType.OBJECT_LIST || field.itemKey() == null) {
                throw new IllegalArgumentException("Field '" + fieldPath + "' must be an object list with an itemKey"
                        + " to be stored as a keyed section");
            }
            keyedSections.add(fieldPath);
            return this;
        }

        public SectionMapper build() {
            return new SectionMapper(schema, Set.copyOf(keyedSections));
        }
    }
}
