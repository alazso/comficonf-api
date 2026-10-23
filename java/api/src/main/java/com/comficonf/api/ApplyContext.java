package com.comficonf.api;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Input of {@link ConfigProvider#apply}. Provided by the bridge; valid only during the apply call.
 */
public interface ApplyContext {

    /** The resource being applied. */
    ResourceId resourceId();

    /** Unique id of this operation. A retried submission carries the same id and is not applied twice. */
    String operationId();

    /** Revision the editor started from. It matched the stored revision when the operation started. */
    String baseRevision();

    /**
     * Values read at the start of the operation, including sensitive values and keys the schema does not
     * declare. Numbers of declared {@code integer} and {@code decimal} fields are converted to the declared type
     * the same way as for the editor: integers to {@link Double} for {@code decimal} fields, and whole
     * {@link Double} values within ±(2<sup>53</sup> - 1) to {@link Long} for {@code integer} fields. An unchanged
     * number therefore equals its value in {@link #proposed()}. All other values are as {@code read()} returned
     * them and are not checked against the schema.
     */
    ConfigValues current();

    /** Complete validated values to persist, including unchanged and sensitive values. */
    ConfigValues proposed();

    /** Differences between {@link #current()} and {@link #proposed()}. Never empty. */
    List<FieldChange> changes();

    /** Whether any change touches a field declared with {@link com.comficonf.api.schema.ApplyMode#RESTART}. */
    boolean requiresRestart();

    /** Account that requested the operation. */
    Actor actor();

    /**
     * Runs {@code task} on the server main thread and waits for its result. Runs it directly when called on the
     * main thread.
     *
     * @throws java.util.concurrent.TimeoutException if the main thread does not complete the task within the
     *     bridge's timeout
     * @throws Exception the exception thrown by {@code task}
     */
    <T> T callSync(Callable<T> task) throws Exception;

    /** Runs {@code task} on the server main thread and waits for it to finish. See {@link #callSync}. */
    default void runSync(Runnable task) throws Exception {
        callSync(() -> {
            task.run();
            return null;
        });
    }
}
