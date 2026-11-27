package org.cda.archi.governance.infrastructure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.cda.archi.governance.models.GitCredential;

/** Infrastructure adapter for the standard `git credential fill` stdin/stdout protocol. */
public final class GitCredentialAdapter {
    private static final Duration CREDENTIAL_TIMEOUT = Duration.ofMinutes(2);
    private static final byte[] REQUEST = "protocol=https\nhost=github.com\n\n".getBytes(StandardCharsets.UTF_8);
    private final CommandRunner runner;

    public GitCredentialAdapter() {
        this(new ProcessExecutor());
    }

    public GitCredentialAdapter(CommandRunner runner) {
        this.runner = runner;
    }

    public GitCredential fill(CancellationToken cancellation) throws IOException {
        try (ProcessResult result = runner.run(List.of("git", "credential", "fill"), REQUEST,
                CREDENTIAL_TIMEOUT, cancellation)) {
            if (result.exitCode() != 0) {
                throw new IOException("Git's credential manager could not provide credentials. It may have been cancelled.");
            }
            return parseAndWipe(result.standardOutput());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Git credential retrieval was cancelled.");
        } catch (java.util.concurrent.CancellationException ex) {
            throw new IOException("Git credential retrieval was cancelled.");
        }
    }

    private static GitCredential parseAndWipe(byte[] output) throws IOException {
        char[] chars = null;
        char[] password = null;
        String username = "";
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output));
            chars = new char[decoded.remaining()];
            decoded.get(chars);
            int lineStart = 0;
            for (int index = 0; index <= chars.length; index++) {
                if (index != chars.length && chars[index] != '\n') continue;
                int lineEnd = index > lineStart && chars[index - 1] == '\r' ? index - 1 : index;
                int equals = lineStart;
                while (equals < lineEnd && chars[equals] != '=') equals++;
                if (equals < lineEnd) {
                    String key = new String(chars, lineStart, equals - lineStart);
                    if ("username".equals(key)) username = new String(chars, equals + 1, lineEnd - equals - 1);
                    if ("password".equals(key)) {
                        if (password != null) Arrays.fill(password, '\0');
                        password = Arrays.copyOfRange(chars, equals + 1, lineEnd);
                    }
                }
                lineStart = index + 1;
            }
            if (password == null || password.length == 0) {
                throw new IOException("Git's credential helper returned no password/token.");
            }
            return new GitCredential(username, password);
        } catch (CharacterCodingException ex) {
            throw new IOException("Git's credential helper returned an unreadable response.");
        } finally {
            Arrays.fill(output, (byte) 0);
            if (chars != null) Arrays.fill(chars, '\0');
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}
