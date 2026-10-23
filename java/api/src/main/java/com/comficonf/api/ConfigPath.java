package com.comficonf.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Paths addressing values inside a {@link ConfigValues} tree.
 *
 * <p>A path joins field keys with {@code .} and addresses list items with {@code [index]}, for example
 * {@code settings.prefix} or {@code vouchers[2].cooldown-seconds}. The empty path addresses the whole
 * resource. Field keys never contain {@code .}, {@code [} or {@code ]}, so paths are unambiguous.
 */
public final class ConfigPath {

    /** Pattern every field key must match. */
    public static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private ConfigPath() {
    }

    /** Returns the path of {@code key} inside the object at {@code parent}. */
    public static String child(String parent, String key) {
        Objects.requireNonNull(parent, "parent");
        requireKey(key);
        return parent.isEmpty() ? key : parent + "." + key;
    }

    /** Returns the path of item {@code index} of the list at {@code parent}. */
    public static String index(String parent, int index) {
        Objects.requireNonNull(parent, "parent");
        if (parent.isEmpty()) {
            throw new IllegalArgumentException("A list index needs a parent path");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        return parent + "[" + index + "]";
    }

    /**
     * Splits a path into segments: {@link String} for keys and {@link Integer} for list indexes.
     *
     * @throws IllegalArgumentException if the path is malformed
     */
    public static List<Object> parse(String path) {
        Objects.requireNonNull(path, "path");
        List<Object> segments = new ArrayList<>();
        if (path.isEmpty()) {
            return segments;
        }
        int i = 0;
        int length = path.length();
        boolean expectKey = true;
        while (i < length) {
            char c = path.charAt(i);
            if (c == '[') {
                if (segments.isEmpty() || expectKey) {
                    throw malformed(path);
                }
                int end = path.indexOf(']', i);
                if (end < 0 || end == i + 1) {
                    throw malformed(path);
                }
                String digits = path.substring(i + 1, end);
                if (!digits.chars().allMatch(d -> d >= '0' && d <= '9') || digits.length() > 9) {
                    throw malformed(path);
                }
                segments.add(Integer.parseInt(digits));
                i = end + 1;
                expectKey = false;
            } else if (c == '.') {
                if (segments.isEmpty() || expectKey) {
                    throw malformed(path);
                }
                i++;
                expectKey = true;
                if (i == length) {
                    throw malformed(path);
                }
            } else {
                if (!expectKey) {
                    throw malformed(path);
                }
                int end = i;
                while (end < length && path.charAt(end) != '.' && path.charAt(end) != '[') {
                    end++;
                }
                String key = path.substring(i, end);
                if (!KEY_PATTERN.matcher(key).matches()) {
                    throw malformed(path);
                }
                segments.add(key);
                i = end;
                expectKey = false;
            }
        }
        return segments;
    }

    /** Returns whether {@code key} is a valid field key. */
    public static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    static void requireKey(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Invalid field key '" + key
                    + "': use 1-64 letters, digits, '_' or '-'");
        }
    }

    private static IllegalArgumentException malformed(String path) {
        return new IllegalArgumentException("Malformed config path '" + path + "'");
    }
}
