package com.comficonf.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable tree of configuration values.
 *
 * <p>Values are normalized on construction to these types:
 * <ul>
 *   <li>{@link String} and {@link Boolean};</li>
 *   <li>{@link Long} for {@code byte}, {@code short}, {@code int}, {@code long} and {@link BigInteger} values
 *       that fit in a {@code long};</li>
 *   <li>{@link Double} for {@code float}, {@code double} and {@link BigDecimal};</li>
 *   <li>an unmodifiable {@link List} without null elements;</li>
 *   <li>an unmodifiable {@link Map} with {@link String} keys, preserving insertion order;</li>
 *   <li>{@code null} for an explicitly unset value.</li>
 * </ul>
 * Any other type is rejected with {@link IllegalArgumentException}. {@link #asMap()} exposes nested objects as
 * plain maps; the typed getters wrap them in {@code ConfigValues}.
 */
public final class ConfigValues {

    private static final ConfigValues EMPTY = new ConfigValues(Map.of());

    private final Map<String, @Nullable Object> values;

    private ConfigValues(Map<String, @Nullable Object> normalized) {
        this.values = normalized;
    }

    /** Returns an empty value tree. */
    public static ConfigValues empty() {
        return EMPTY;
    }

    /**
     * Creates a value tree from a map, copying and normalizing it deeply.
     *
     * @throws IllegalArgumentException if the map contains unsupported types, non-string keys or invalid keys
     */
    public static ConfigValues of(Map<String, ?> map) {
        Objects.requireNonNull(map, "map");
        if (map.isEmpty()) {
            return EMPTY;
        }
        return new ConfigValues(normalizeMap(map, ""));
    }

    /** Starts a mutable builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Normalizes a single value according to the rules of this class.
     *
     * @throws IllegalArgumentException if the value has an unsupported type
     */
    public static @Nullable Object normalizeValue(@Nullable Object value) {
        return normalize(value, "");
    }

    /** Returns the values as an unmodifiable map. Nested objects are unmodifiable maps. */
    public Map<String, @Nullable Object> asMap() {
        return values;
    }

    /** Returns the top-level keys. */
    public Set<String> keys() {
        return values.keySet();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Returns whether a value, possibly null, exists at {@code path}. */
    public boolean contains(String path) {
        return lookup(path) != MISSING;
    }

    /**
     * Returns the value at {@code path}, or null when it is missing or null.
     *
     * @throws IllegalArgumentException if the path is malformed
     */
    public @Nullable Object get(String path) {
        Object value = lookup(path);
        return value == MISSING ? null : value;
    }

    /**
     * Returns the string at {@code path}.
     *
     * @throws NoSuchElementException if the value is missing or null
     * @throws IllegalStateException if the value is not a string
     */
    public String getString(String path) {
        return require(path, String.class);
    }

    /** Returns the string at {@code path}, or {@code fallback} when it is missing or null. */
    public String getString(String path, String fallback) {
        Object value = get(path);
        return value == null ? fallback : cast(path, value, String.class);
    }

    /**
     * Returns the boolean at {@code path}.
     *
     * @throws NoSuchElementException if the value is missing or null
     * @throws IllegalStateException if the value is not a boolean
     */
    public boolean getBoolean(String path) {
        return require(path, Boolean.class);
    }

    /** Returns the boolean at {@code path}, or {@code fallback} when it is missing or null. */
    public boolean getBoolean(String path, boolean fallback) {
        Object value = get(path);
        return value == null ? fallback : cast(path, value, Boolean.class);
    }

    /**
     * Returns the integer at {@code path}.
     *
     * @throws NoSuchElementException if the value is missing or null
     * @throws IllegalStateException if the value is not an integer
     */
    public long getLong(String path) {
        return require(path, Long.class);
    }

    /** Returns the integer at {@code path}, or {@code fallback} when it is missing or null. */
    public long getLong(String path, long fallback) {
        Object value = get(path);
        return value == null ? fallback : cast(path, value, Long.class);
    }

    /**
     * Returns the integer at {@code path} as an {@code int}.
     *
     * @throws NoSuchElementException if the value is missing or null
     * @throws IllegalStateException if the value is not an integer or does not fit in an {@code int}
     */
    public int getInt(String path) {
        return toInt(path, getLong(path));
    }

    /** Returns the integer at {@code path} as an {@code int}, or {@code fallback} when it is missing or null. */
    public int getInt(String path, int fallback) {
        Object value = get(path);
        return value == null ? fallback : toInt(path, cast(path, value, Long.class));
    }

    /**
     * Returns the number at {@code path} as a {@code double}. Integers are converted.
     *
     * @throws NoSuchElementException if the value is missing or null
     * @throws IllegalStateException if the value is not a number
     */
    public double getDouble(String path) {
        Object value = get(path);
        if (value == null) {
            throw new NoSuchElementException("No value at '" + path + "'");
        }
        return toDouble(path, value);
    }

    /** Returns the number at {@code path} as a {@code double}, or {@code fallback} when it is missing or null. */
    public double getDouble(String path, double fallback) {
        Object value = get(path);
        return value == null ? fallback : toDouble(path, value);
    }

    /**
     * Returns the string list at {@code path}, or an empty list when it is missing or null.
     *
     * @throws IllegalStateException if the value is not a list of strings
     */
    public List<String> getStringList(String path) {
        Object value = get(path);
        if (value == null) {
            return List.of();
        }
        List<?> list = cast(path, value, List.class);
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new IllegalStateException("Expected a list of strings at '" + path + "'");
            }
        }
        @SuppressWarnings("unchecked")
        List<String> strings = (List<String>) list;
        return strings;
    }

    /**
     * Returns the object at {@code path}, or an empty tree when it is missing or null.
     *
     * @throws IllegalStateException if the value is not an object
     */
    public ConfigValues getObject(String path) {
        Object value = get(path);
        if (value == null) {
            return EMPTY;
        }
        return wrap(path, value);
    }

    /**
     * Returns the object list at {@code path}, or an empty list when it is missing or null.
     *
     * @throws IllegalStateException if the value is not a list of objects
     */
    public List<ConfigValues> getObjectList(String path) {
        Object value = get(path);
        if (value == null) {
            return List.of();
        }
        List<?> list = cast(path, value, List.class);
        List<ConfigValues> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(wrap(ConfigPath.index(path, i), list.get(i)));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns a copy with {@code value} stored at {@code path}. Missing parent objects are created. List items
     * along the path must already exist.
     *
     * @throws IllegalArgumentException if the path is empty or malformed, crosses a non-container value, or the
     *     value has an unsupported type
     */
    public ConfigValues with(String path, @Nullable Object value) {
        List<Object> segments = ConfigPath.parse(path);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Cannot replace the root with with(); use ConfigValues.of()");
        }
        Object normalized = normalize(value, path);
        Object updated = set(values, segments, 0, normalized, path, false);
        @SuppressWarnings("unchecked")
        Map<String, @Nullable Object> map = (Map<String, @Nullable Object>) updated;
        return new ConfigValues(map);
    }

    /**
     * Returns a copy without the value at {@code path}. Returns this instance when nothing is stored there.
     *
     * @throws IllegalArgumentException if the path is empty, malformed or addresses a list item
     */
    public ConfigValues without(String path) {
        List<Object> segments = ConfigPath.parse(path);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Cannot remove the root");
        }
        if (segments.getLast() instanceof Integer) {
            throw new IllegalArgumentException("without() removes object keys, not list items: '" + path + "'");
        }
        if (!contains(path)) {
            return this;
        }
        Object updated = set(values, segments, 0, null, path, true);
        @SuppressWarnings("unchecked")
        Map<String, @Nullable Object> map = (Map<String, @Nullable Object>) updated;
        return new ConfigValues(map);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigValues that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    private static final Object MISSING = new Object();

    private Object lookup(String path) {
        List<Object> segments = ConfigPath.parse(path);
        Object current = values;
        for (Object segment : segments) {
            if (segment instanceof String key) {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(key)) {
                    return MISSING;
                }
                current = map.get(key);
            } else {
                int index = (Integer) segment;
                if (!(current instanceof List<?> list) || index >= list.size()) {
                    return MISSING;
                }
                current = list.get(index);
            }
        }
        return current;
    }

    private <T> T require(String path, Class<T> type) {
        Object value = get(path);
        if (value == null) {
            throw new NoSuchElementException("No value at '" + path + "'");
        }
        return cast(path, value, type);
    }

    private static <T> T cast(String path, Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + " at '" + path + "' but found "
                    + value.getClass().getSimpleName());
        }
        return type.cast(value);
    }

    private static int toInt(String path, long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Value at '" + path + "' does not fit in an int: " + value);
        }
        return (int) value;
    }

    private static double toDouble(String path, Object value) {
        if (value instanceof Long l) {
            return l.doubleValue();
        }
        return cast(path, value, Double.class);
    }

    private static ConfigValues wrap(String path, @Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected an object at '" + path + "'");
        }
        @SuppressWarnings("unchecked")
        Map<String, @Nullable Object> normalized = (Map<String, @Nullable Object>) map;
        return normalized.isEmpty() ? EMPTY : new ConfigValues(normalized);
    }

    private static Object set(Object container, List<Object> segments, int position, @Nullable Object value,
            String path, boolean remove) {
        Object segment = segments.get(position);
        boolean last = position == segments.size() - 1;
        if (segment instanceof String key) {
            if (!(container instanceof Map<?, ?> source)) {
                throw new IllegalArgumentException("Path '" + path + "' crosses a value that is not an object");
            }
            Map<String, @Nullable Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put((String) entry.getKey(), entry.getValue());
            }
            if (last) {
                if (remove) {
                    copy.remove(key);
                } else {
                    copy.put(key, value);
                }
            } else {
                Object child = copy.get(key);
                if (child == null) {
                    if (segments.get(position + 1) instanceof Integer) {
                        throw new IllegalArgumentException("Path '" + path + "' addresses a missing list item");
                    }
                    child = Map.of();
                }
                copy.put(key, set(child, segments, position + 1, value, path, remove));
            }
            return Collections.unmodifiableMap(copy);
        }
        int index = (Integer) segment;
        if (!(container instanceof List<?> source) || index >= source.size()) {
            throw new IllegalArgumentException("Path '" + path + "' addresses a missing list item");
        }
        List<@Nullable Object> copy = new ArrayList<>(source);
        if (last) {
            if (value == null) {
                throw new IllegalArgumentException("List items must not be null: '" + path + "'");
            }
            copy.set(index, value);
        } else {
            copy.set(index, set(source.get(index), segments, position + 1, value, path, remove));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, @Nullable Object> normalizeMap(Map<?, ?> map, String path) {
        Map<String, @Nullable Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Object keys must be strings at '" + path + "'");
            }
            if (!ConfigPath.isValidKey(key)) {
                throw new IllegalArgumentException("Invalid key '" + key + "' at '" + path + "'");
            }
            String childPath = path.isEmpty() ? key : path + "." + key;
            copy.put(key, normalize(entry.getValue(), childPath));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object normalize(@Nullable Object value, String path) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Boolean b -> b;
            case Long l -> l;
            case Integer i -> i.longValue();
            case Short s -> s.longValue();
            case Byte b -> b.longValue();
            case BigInteger big -> {
                if (big.bitLength() > 63) {
                    throw new IllegalArgumentException("Integer at '" + path + "' does not fit in a long");
                }
                yield big.longValue();
            }
            case Double d -> requireFinite(d, path);
            case Float f -> requireFinite(f.doubleValue(), path);
            case BigDecimal decimal -> requireFinite(decimal.doubleValue(), path);
            case ConfigValues nested -> nested.values;
            case Map<?, ?> map -> normalizeMap(map, path);
            case List<?> list -> {
                List<Object> copy = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String itemPath = path + "[" + i + "]";
                    if (item == null) {
                        throw new IllegalArgumentException("List items must not be null: '" + itemPath + "'");
                    }
                    copy.add(normalize(item, itemPath));
                }
                yield Collections.unmodifiableList(copy);
            }
            default -> throw new IllegalArgumentException("Unsupported value type "
                    + value.getClass().getName() + " at '" + path + "'");
        };
    }

    private static Double requireFinite(double value, String path) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Number at '" + path + "' must be finite");
        }
        return value;
    }

    /** Mutable builder of {@link ConfigValues}. Not thread-safe. */
    public static final class Builder {
        private ConfigValues current = EMPTY;

        private Builder() {
        }

        /**
         * Stores {@code value} at {@code path}, creating parent objects as needed.
         *
         * @throws IllegalArgumentException as described for {@link ConfigValues#with}
         */
        public Builder put(String path, @Nullable Object value) {
            current = current.with(path, value);
            return this;
        }

        /** Stores all top-level entries of {@code map}. */
        public Builder putAll(Map<String, ?> map) {
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ConfigValues build() {
            return current;
        }
    }
}
