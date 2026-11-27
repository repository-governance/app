package org.cda.archi.githubworkspace.infrastructure;

import java.util.Arrays;

/** Captured process output. Call clear() promptly when output may contain secrets. */
public final class ProcessResult implements AutoCloseable {
    private final int exitCode;
    private byte[] standardOutput;
    private byte[] standardError;

    public ProcessResult(int exitCode, byte[] standardOutput, byte[] standardError) {
        this.exitCode = exitCode;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
    }

    public int exitCode() { return exitCode; }
    public byte[] standardOutput() { return standardOutput; }
    public byte[] standardError() { return standardError; }

    @Override
    public void close() {
        Arrays.fill(standardOutput, (byte) 0);
        Arrays.fill(standardError, (byte) 0);
        standardOutput = new byte[0];
        standardError = new byte[0];
    }
}
