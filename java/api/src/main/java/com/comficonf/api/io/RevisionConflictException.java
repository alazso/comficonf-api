package com.comficonf.api.io;

import java.io.Serial;

/** Thrown by {@link SafeFileWriter} when the file no longer has the expected revision. Nothing was written. */
public final class RevisionConflictException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String expectedRevision;
    private final String currentRevision;

    public RevisionConflictException(String expectedRevision, String currentRevision) {
        super("Expected revision " + expectedRevision + " but the file has revision " + currentRevision);
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
    }

    public String expectedRevision() {
        return expectedRevision;
    }

    /** Revision of the file's content at the time of the check. */
    public String currentRevision() {
        return currentRevision;
    }
}
