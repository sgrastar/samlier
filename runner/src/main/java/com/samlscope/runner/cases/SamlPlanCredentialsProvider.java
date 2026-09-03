package com.samlscope.runner.cases;

import java.util.Optional;
import com.samlscope.saml.crypto.PlanCredentials;

@FunctionalInterface
public interface SamlPlanCredentialsProvider {
    Optional<PlanCredentials> credentialsFor(String runId);
}
