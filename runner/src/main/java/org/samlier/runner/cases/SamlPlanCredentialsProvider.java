package org.samlier.runner.cases;

import java.util.Optional;
import org.samlier.saml.crypto.PlanCredentials;

@FunctionalInterface
public interface SamlPlanCredentialsProvider {
    Optional<PlanCredentials> credentialsFor(String runId);
}
