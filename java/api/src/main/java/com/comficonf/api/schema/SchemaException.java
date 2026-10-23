package com.comficonf.api.schema;

import java.io.Serial;

/** Thrown when a schema definition is invalid. Raised while building fields and schemas. */
public final class SchemaException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SchemaException(String message) {
        super(message);
    }
}
