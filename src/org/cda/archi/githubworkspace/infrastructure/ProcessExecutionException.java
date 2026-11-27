package org.cda.archi.githubworkspace.infrastructure;

import java.io.IOException;

/** Deliberately omits process output so credentials cannot leak in diagnostics. */
public final class ProcessExecutionException extends IOException {
    public ProcessExecutionException(String message) {
        super(message);
    }
}
