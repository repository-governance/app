package org.cda.archi.governance.models;

import java.util.Arrays;

/** Short-lived credential response from `git credential fill`; never persist or log. */
public final class GitCredential implements AutoCloseable {
    private final String username;
    private char[] password;

    public GitCredential(String username, char[] password) {
        this.username = username == null ? "" : username;
        this.password = password.clone();
    }

    public String getUsername() { return username; }

    /** Internal use only; caller must clear this credential immediately after request. */
    public char[] copyPassword() { return password.clone(); }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
        password = new char[0];
    }

    @Override
    public String toString() { return "GitCredential[redacted]"; }
}
