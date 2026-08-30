package org.samlier.runner.cases;

import java.time.Duration;
import java.util.List;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.runner.BrowserFrontChannelScenario;
import org.samlier.runner.scenario.FixtureObservation;
import org.samlier.runner.scenario.FixtureScenarioTestCase;
import org.samlier.runner.scenario.ScenarioFixture;
import org.samlier.saml.normal.SamlDestinationRequestFactory;
import org.samlier.saml.normal.SamlDestinationRequestFactory.Fixture;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Browser-driven Destination matrix with no operator-entered verdict. */
public final class IdpDestinationScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final String CASE_ID = "IIP-SSO01-ag-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlDestinationRequestFactory requests;

    public IdpDestinationScenarioTestCase(
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(configurations, new SamlDestinationRequestFactory());
    }

    IdpDestinationScenarioTestCase(
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlDestinationRequestFactory requests) {
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        return new FixtureScenarioTestCase(
                CASE_ID, TargetRole.IDP,
                java.util.Arrays.stream(Fixture.values())
                        .<ScenarioFixture>map(value -> new DestinationFixture(value, configuration, requests))
                        .toList(),
                ignored -> configuration.preconditionsSatisfied(),
                new FixtureScenarioTestCase.Vocabulary(
                        "destination_preconditions_unmet", "idp.destination.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.destination.delivery-unknown",
                        "scenario_aborted", "idp.destination.aborted",
                        "case.idp.destination.control-failed",
                        "destination_validation_violated", "case.idp.destination.violated",
                        "destination_result_not_conclusive", "idp.destination.inconclusive",
                        "case.idp.destination.inconclusive",
                        "idp.destination.satisfied", "case.idp.destination.satisfied"));
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return "Log in for the valid control. Samlier then delivers valid AuthnRequests whose Destination names a different host, another target endpoint, and another IdP. If the target shows a terminal error instead of returning SAML, use 'No SAML Response was returned'.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private record DestinationFixture(
            Fixture fixture,
            IdpErrorProbeConfiguration configuration,
            SamlDestinationRequestFactory requests) implements ScenarioFixture {
        @Override public String id() {
            return fixture.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }

        @Override public Prepared prepare(CaseContext context, String actionId) {
            var requestId = "_" + actionId;
            return new Prepared(new OutboundAction(
                    actionId, OutboundKind.AUTHN_REQUEST,
                    requests.build(fixture, requestId, configuration.ssoEndpoint(),
                            configuration.suiteIssuer(), configuration.registeredAcs(),
                            context.clock().instant()),
                    configuration.ssoEndpoint(), false), requestId);
        }

        @Override public FixtureObservation observe(String requestId, byte[] responseXml) {
            try {
                var root = SecureXml.parse(responseXml).getDocumentElement();
                if (!PROTOCOL.equals(root.getNamespaceURI()) || !"Response".equals(root.getLocalName())
                        || !requestId.equals(root.getAttribute("InResponseTo"))) {
                    return FixtureObservation.NOT_VERIFIED;
                }
                var codes = root.getElementsByTagNameNS(PROTOCOL, "StatusCode");
                if (codes.getLength() == 0) return FixtureObservation.NOT_VERIFIED;
                var success = SUCCESS.equals(((Element) codes.item(0)).getAttribute("Value"));
                var assertions = root.getElementsByTagNameNS(ASSERTION, "Assertion").getLength()
                        + root.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength();
                if (fixture == Fixture.BASELINE) {
                    return success && assertions > 0
                            ? FixtureObservation.SATISFIED : FixtureObservation.CONTROL_FAILED;
                }
                return success && assertions > 0
                        ? FixtureObservation.VIOLATED : FixtureObservation.SATISFIED;
            } catch (SamlException malformed) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public FixtureObservation observeUnavailable(String reason) {
            if (!"operator-reported-no-saml-response".equals(reason)) {
                return FixtureObservation.NOT_VERIFIED;
            }
            return fixture == Fixture.BASELINE
                    ? FixtureObservation.CONTROL_FAILED : FixtureObservation.SATISFIED;
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", fixture.name(), configuration.ssoEndpoint().toString(),
                    configuration.registeredAcs().toString(), "destination-v1");
        }
    }
}
