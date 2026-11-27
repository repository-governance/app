package org.cda.archi.githubworkspace.models;

/** Read-only snapshot of the local Git identity and credential-helper setup. */
public final class GitConfiguration {
    private final boolean available;
    private final String version;
    private final String userName;
    private final String userEmail;
    private final boolean credentialHelperConfigured;
    private final String message;

    public GitConfiguration(boolean available, String version, String userName, String userEmail,
            boolean credentialHelperConfigured, String message) {
        this.available = available;
        this.version = value(version);
        this.userName = value(userName);
        this.userEmail = value(userEmail);
        this.credentialHelperConfigured = credentialHelperConfigured;
        this.message = value(message);
    }

    public boolean isAvailable() { return available; }
    public String getVersion() { return version; }
    public String getUserName() { return userName; }
    public String getUserEmail() { return userEmail; }
    public boolean hasCredentialHelper() { return credentialHelperConfigured; }
    public String getMessage() { return message; }

    private static String value(String input) { return input == null ? "" : input; }
}
