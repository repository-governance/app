package org.cda.archi.governance.models;

/** UI-facing authentication state; never contains credential material. */
public final class AuthenticationStatus {
    public enum State { NOT_CONNECTED, AUTHENTICATING, AUTHENTICATED, FAILED, CANCELLED }

    private final State state;
    private final String message;
    private final GitHubUser user;

    public AuthenticationStatus(State state, String message, GitHubUser user) {
        this.state = state;
        this.message = message == null ? "" : message;
        this.user = user;
    }

    public State getState() { return state; }
    public String getMessage() { return message; }
    public GitHubUser getUser() { return user; }
    public boolean isAuthenticated() { return state == State.AUTHENTICATED && user != null; }
}
