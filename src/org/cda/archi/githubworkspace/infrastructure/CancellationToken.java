package org.cda.archi.githubworkspace.infrastructure;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation shared by UI controllers and infrastructure calls. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Operation cancelled.");
        }
    }
}
