package org.cda.archi.githubworkspace.services;

import java.io.IOException;

/** Signals that showing the received subset would falsely imply a complete search. */
public final class IncompleteSearchException extends IOException {
    public IncompleteSearchException(String message) { super(message); }
}
