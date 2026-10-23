package com.comficonf.api;

import java.util.Objects;

/**
 * Account that requested an operation through the ComfiConf website.
 *
 * @param id opaque, stable account id
 * @param label human-readable label for server logs, such as the account email
 */
public record Actor(String id, String label) {

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }
}
