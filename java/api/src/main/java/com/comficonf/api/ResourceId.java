package com.comficonf.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable identifier of a configuration resource, in the form {@code namespace:name}, for example
 * {@code comficonf-demo:vouchers}.
 *
 * <p>Both parts are 1 to 64 characters of lowercase letters, digits, {@code .}, {@code _} and {@code -}, and
 * start with a letter or digit. The id must not change between plugin versions, because the web editor and
 * audit history refer to it.
 *
 * @param namespace the namespace, usually the plugin name or package
 * @param name the resource name within the namespace
 */
public record ResourceId(String namespace, String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (!PART.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace '" + namespace
                    + "': use 1-64 lowercase letters, digits, '.', '_' or '-'");
        }
        if (!PART.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid resource name '" + name
                    + "': use 1-64 lowercase letters, digits, '.', '_' or '-'");
        }
    }

    /** Creates an id from its two parts. */
    public static ResourceId of(String namespace, String name) {
        return new ResourceId(namespace, name);
    }

    /**
     * Parses {@code namespace:name}.
     *
     * @throws IllegalArgumentException if the value is not a valid id
     */
    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator < 0 || separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Invalid resource id '" + value + "': expected namespace:name");
        }
        return new ResourceId(value.substring(0, separator), value.substring(separator + 1));
    }

    /** Returns {@code namespace:name}. */
    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
