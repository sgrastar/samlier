package com.samlscope.runner.cases;

import java.time.Duration;
import java.util.List;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.OutboundAction;
import com.samlscope.core.caseexec.OutboundKind;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.BrowserFrontChannelScenario;
import com.samlscope.runner.scenario.FixtureObservation;
import com.samlscope.runner.scenario.FixtureScenarioTestCase;
import com.samlscope.runner.scenario.ScenarioFixture;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SamlInvalidRequestFactory;
import com.samlscope.saml.normal.SamlInvalidRequestFactory.Fixture;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Executes malformed AuthnRequests; the operator performs only browser navigation/login. */
public final class IdpInvalidRequestScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final String STATUS_CASE = "IIP-SSO01-an-idp-01";
    public static final String CORRELATION_CASE = "IIP-SSO01-gi-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final String REQUESTER = "urn:oasis:names:tc:SAML:2.0:status:Requester";
    private final String id;
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlInvalidRequestFactory requests;

    public IdpInvalidRequestScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(id, configurations, new SamlInvalidRequestFactory());
    }

    IdpInvalidRequestScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlInvalidRequestFactory requests) {
        if (!List.of(STATUS_CASE, CORRELATION_CASE).contains(id)) {
            throw new IllegalArgumentException("Unsupported invalid-request case: " + id);
        }
        this.id = id;
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        var fixtures = id.equals(STATUS_CASE)
                ? List.of(Fixture.BASELINE, Fixture.MISSING_ID, Fixture.UNSUPPORTED_VERSION)
                : List.of(Fixture.BASELINE, Fixture.MISSING_ID);
        return new FixtureScenarioTestCase(
                id, TargetRole.IDP,
                fixtures.stream().<ScenarioFixture>map(value ->
                        new InvalidRequestFixture(id, value, configuration, requests)).toList(),
                ignored -> configuration.preconditionsSatisfied(),
                new FixtureScenarioTestCase.Vocabulary(
                        "invalid_request_preconditions_unmet", "idp.invalid-request.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.invalid-request.delivery-unknown",
                        "scenario_aborted", "idp.invalid-request.aborted",
                        "case.idp.invalid-request.control-failed",
                        id.equals(STATUS_CASE)
                                ? "invalid_request_status_violated" : "indeterminate_request_correlation_violated",
                        "case.idp.invalid-request.violated",
                        "invalid_request_result_not_conclusive", "idp.invalid-request.inconclusive",
                        "case.idp.invalid-request.inconclusive",
                        "idp.invalid-request.satisfied", "case.idp.invalid-request.satisfied"));
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return id.equals(STATUS_CASE)
                ? "Log in for the valid control. SAMLscope then sends schema-invalid and unsupported-version requests and checks any correlated SAML status automatically."
                : "Log in for the valid control. SAMLscope then sends an AuthnRequest without ID and checks any returned Response for an absent InResponseTo value.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private record InvalidRequestFixture(
            String caseId,
            Fixture fixture,
            IdpErrorProbeConfiguration configuration,
            SamlInvalidRequestFactory requests) implements ScenarioFixture {
        @Override public String id() {
            return fixture.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }

        @Override public Prepared prepare(CaseContext context, String actionId) {
            var requestId = "_" + actionId;
            var payload = requests.build(
                    fixture, requestId, configuration.ssoEndpoint(), configuration.suiteIssuer(),
                    configuration.registeredAcs(), context.clock().instant());
            return new Prepared(new OutboundAction(
                    actionId, OutboundKind.AUTHN_REQUEST, payload,
                    configuration.ssoEndpoint(), false), requestId);
        }

        @Override public FixtureObservation observe(String requestId, byte[] responseXml) {
            try {
                var root = SecureXml.parse(responseXml).getDocumentElement();
                if (!PROTOCOL.equals(root.getNamespaceURI()) || !"Response".equals(root.getLocalName())) {
                    return FixtureObservation.NOT_VERIFIED;
                }
                var statusCodes = root.getElementsByTagNameNS(PROTOCOL, "StatusCode");
                if (statusCodes.getLength() == 0) return FixtureObservation.NOT_VERIFIED;
                var top = ((Element) statusCodes.item(0)).getAttribute("Value");
                if (fixture == Fixture.BASELINE) {
                    var assertions = root.getElementsByTagNameNS(ASSERTION, "Assertion").getLength()
                            + root.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength();
                    return requestId.equals(root.getAttribute("InResponseTo"))
                            && SUCCESS.equals(top) && assertions > 0
                            ? FixtureObservation.SATISFIED : FixtureObservation.CONTROL_FAILED;
                }
                if (caseId.equals(CORRELATION_CASE)) {
                    return root.getAttribute("InResponseTo").isBlank()
                            ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
                }
                return REQUESTER.equals(top)
                        ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
            } catch (SamlException malformed) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public FixtureObservation observeUnavailable(String reason) {
            if (!"operator-reported-no-saml-response".equals(reason)) {
                return FixtureObservation.NOT_VERIFIED;
            }
            // Both incorporated Core rules are conditional on a SAML Response being returned.
            // A terminal HTTP/HTML error therefore is not target nonconformance for these cases.
            return fixture == Fixture.BASELINE
                    ? FixtureObservation.CONTROL_FAILED : FixtureObservation.SATISFIED;
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", caseId, fixture.name(), configuration.ssoEndpoint().toString(),
                    configuration.registeredAcs().toString());
        }
    }
}
