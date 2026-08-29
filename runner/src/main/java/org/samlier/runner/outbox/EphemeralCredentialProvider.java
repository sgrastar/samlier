package org.samlier.runner.outbox;

import java.util.Optional;

public interface EphemeralCredentialProvider {
    Optional<byte[]> credentialFor(String runId, String actionId);
}
