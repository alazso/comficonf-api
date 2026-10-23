package com.comficonf.demo.config;

import java.io.Serial;

/** The configuration file cannot be turned into a runtime configuration. The message names the problem. */
public final class InvalidConfigException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidConfigException(String message) {
        super(message);
    }

    public InvalidConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
