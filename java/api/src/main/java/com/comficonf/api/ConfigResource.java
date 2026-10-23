package com.comficonf.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Descriptor of a configuration resource: its identity, display metadata and provider. Immutable. */
public final class ConfigResource {

    private static final int MAX_DISPLAY_NAME = 80;
    private static final int MAX_DESCRIPTION = 500;

    private final ResourceId id;
    private final String displayName;
    private final @Nullable String description;
    private final ConfigProvider provider;
    private final CallbackThread callbackThread;

    private ConfigResource(Builder builder) {
        this.id = builder.id;
        this.displayName = requireText(builder.displayName == null ? builder.id.name() : builder.displayName,
                "displayName", MAX_DISPLAY_NAME);
        this.description = builder.description == null ? null
                : requireText(builder.description, "description", MAX_DESCRIPTION);
        this.provider = Objects.requireNonNull(builder.provider, "provider");
        this.callbackThread = builder.callbackThread;
    }

    /** Starts a builder for the resource {@code namespace:name}. */
    public static Builder builder(String id) {
        return new Builder(ResourceId.parse(id));
    }

    /** Starts a builder for the given resource id. */
    public static Builder builder(ResourceId id) {
        return new Builder(id);
    }

    public ResourceId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public @Nullable String description() {
        return description;
    }

    public ConfigProvider provider() {
        return provider;
    }

    public CallbackThread callbackThread() {
        return callbackThread;
    }

    @Override
    public String toString() {
        return "ConfigResource[" + id + "]";
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    /** Builder of {@link ConfigResource}. Not thread-safe. */
    public static final class Builder {
        private final ResourceId id;
        private @Nullable String displayName;
        private @Nullable String description;
        private @Nullable ConfigProvider provider;
        private CallbackThread callbackThread = CallbackThread.WORKER;

        private Builder(ResourceId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Name shown in the editor, at most 80 characters. Defaults to the resource name. */
        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        /** Short description shown in the editor, at most 500 characters. */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /** The callbacks of the resource. Required. */
        public Builder provider(ConfigProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
            return this;
        }

        /** Thread for provider callbacks. Defaults to {@link CallbackThread#WORKER}. */
        public Builder callbackThread(CallbackThread callbackThread) {
            this.callbackThread = Objects.requireNonNull(callbackThread, "callbackThread");
            return this;
        }

        /**
         * Builds the descriptor.
         *
         * @throws IllegalArgumentException if a value is invalid
         * @throws NullPointerException if no provider was set
         */
        public ConfigResource build() {
            return new ConfigResource(this);
        }
    }
}
