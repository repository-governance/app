package org.cda.archi.governance.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Executes argument-vector processes without a shell and drains both output streams. */
public final class ProcessExecutor implements CommandRunner {
    private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;

    @Override
    public ProcessResult run(List<String> arguments, byte[] standardInput, Duration timeout,
            CancellationToken cancellation) throws IOException, InterruptedException {
        if (arguments == null || arguments.isEmpty()) throw new IllegalArgumentException("Command is required.");
        cancellation.throwIfCancelled();
        Process process;
        try {
            process = new ProcessBuilder(List.copyOf(arguments)).start();
        } catch (IOException ex) {
            throw new ProcessExecutionException("Could not start the requested process.");
        }

        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));
        CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()));
        long deadline = System.nanoTime() + timeout.toNanos();
        try (OutputStream stdin = process.getOutputStream()) {
            if (standardInput != null && standardInput.length > 0) stdin.write(standardInput);
        } catch (IOException ex) {
            terminate(process);
            throw new ProcessExecutionException("Could not send input to the requested process.");
        }

        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancellation.throwIfCancelled();
                if (System.nanoTime() >= deadline) {
                    terminate(process);
                    throw new ProcessExecutionException("The process timed out.");
                }
            }
            return new ProcessResult(process.exitValue(), await(stdout), await(stderr));
        } catch (InterruptedException | RuntimeException | IOException ex) {
            terminate(process);
            stdout.cancel(true);
            stderr.cancel(true);
            throw ex;
        }
    }

    private static byte[] await(CompletableFuture<byte[]> output) throws IOException, InterruptedException {
        try {
            return output.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new ProcessExecutionException("Could not collect process output.");
        }
    }

    private static byte[] readLimited(InputStream input) {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                int remaining = MAX_CAPTURE_BYTES - output.size();
                if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
            }
            return output.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private static void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
