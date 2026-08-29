package org.samlier.runner.cases;

import java.util.List;

/** Server-owned attestation prompt exposed by an interactive case. */
public interface AttestationPrompt {
    String promptEn();
    List<AttestationOption> options();
}
