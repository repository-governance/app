package org.cda.archi.governance.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Injectable process boundary for Git commands and deterministic tests. */
public interface CommandRunner {
    ProcessResult run(List<String> arguments, byte[] standardInput, Duration timeout,
            CancellationToken cancellation) throws IOException, InterruptedException;
}
