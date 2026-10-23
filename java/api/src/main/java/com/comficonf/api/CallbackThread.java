package com.comficonf.api;

/** Thread on which the bridge invokes the callbacks of a {@link ConfigProvider}. */
public enum CallbackThread {
    /**
     * A bridge worker thread that is never the server main thread. Callbacks of one resource never run
     * concurrently with each other. Disk access belongs here. Use {@link ApplyContext#callSync} for work
     * that must run on the main thread.
     */
    WORKER,
    /**
     * The server main thread. Intended for providers whose state is not thread-safe and whose callbacks are
     * fast. Blocking disk or network access in these callbacks stalls the server tick.
     */
    MAIN
}
