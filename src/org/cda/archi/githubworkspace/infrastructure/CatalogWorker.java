package org.cda.archi.githubworkspace.infrastructure;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Single-thread catalog executor that can be closed with a window and restarted
 * when Eclipse reuses the command handler for a later window opening.
 */
public final class CatalogWorker {
    private final String threadName;
    private ExecutorService executor;

    public CatalogWorker(String threadName) {
        this.threadName = threadName;
        executor = newExecutor();
    }

    public synchronized Future<?> submit(Runnable task) {
        if (executor.isShutdown()) executor = newExecutor();
        return executor.submit(task);
    }

    public synchronized void shutdownNow() {
        if (!executor.isShutdown()) executor.shutdownNow();
    }

    public synchronized boolean isShutdown() {
        return executor.isShutdown();
    }

    private ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }
}
