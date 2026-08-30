package org.samlier.runner.scenario;

import java.time.Duration;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.OutboundAction;

/** One executable fixture in a persisted, ordered Runner scenario. */
public interface ScenarioFixture {
    String id();

    Prepared prepare(CaseContext context, String actionId);

    FixtureObservation observe(String expectedResponseCorrelation, byte[] decodedMessage);

    default String inboundType() { return "saml-response"; }

    default Duration timeout() { return Duration.ofMinutes(5); }

    /** Stable, non-secret material that invalidates a persisted scenario when fixture semantics change. */
    String definitionKey();

    record Prepared(OutboundAction action, String expectedResponseCorrelation) {
        public Prepared {
            Objects.requireNonNull(action, "action");
            if (expectedResponseCorrelation == null || expectedResponseCorrelation.isBlank()) {
                throw new IllegalArgumentException("expectedResponseCorrelation is required");
            }
        }
    }
}
