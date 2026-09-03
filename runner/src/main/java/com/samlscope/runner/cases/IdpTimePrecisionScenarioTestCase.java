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
import com.samlscope.saml.normal.SamlErrorProbeRequestFactory;
import com.samlscope.saml.normal.SamlErrorProbeRequestFactory.Probe;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Compares ordinary and sub-millisecond IssueInstant processing with browser login as needed. */
public final class IdpTimePrecisionScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final String CASE_ID = "IIP-SSO01-eh-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlErrorProbeRequestFactory requests;

    public IdpTimePrecisionScenarioTestCase(
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(configurations, new SamlErrorProbeRequestFactory());
    }

    IdpTimePrecisionScenarioTestCase(
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlErrorProbeRequestFactory requests) {
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        return new FixtureScenarioTestCase(
                CASE_ID, TargetRole.IDP, List.of(
                        fixture("millisecond-control", Probe.BASELINE_SUCCESS, true, configuration),
                        fixture("submillisecond", Probe.SUBMILLISECOND_ISSUE_INSTANT, false, configuration)),
                ignored -> configuration.preconditionsSatisfied(),
                new FixtureScenarioTestCase.Vocabulary(
                        "time_precision_preconditions_unmet", "idp.time-precision.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.time-precision.delivery-unknown",
                        "scenario_aborted", "idp.time-precision.aborted",
                        "case.idp.time-precision.control-failed",
                        "submillisecond_time_rejected", "case.idp.time-precision.violated",
                        "time_precision_not_conclusive", "idp.time-precision.inconclusive",
                        "case.idp.time-precision.inconclusive",
                        "idp.time-precision.satisfied", "case.idp.time-precision.satisfied"));
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return "Log in for the ordinary IssueInstant control and the sub-millisecond request. "
                + "SAMLscope compares the correlated SAML Responses; do not enter a verdict.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private ScenarioFixture fixture(
            String id, Probe probe, boolean control, IdpErrorProbeConfiguration configuration) {
        return new TimeFixture(id, probe, control, configuration, requests);
    }

    private record TimeFixture(
            String id,
            Probe probe,
            boolean control,
            IdpErrorProbeConfiguration configuration,
            SamlErrorProbeRequestFactory requests) implements ScenarioFixture {
        @Override public Prepared prepare(CaseContext context, String actionId) {
            var requestId = "_" + actionId;
            var payload = requests.build(
                    probe, requestId, configuration.ssoEndpoint(), configuration.suiteIssuer(),
                    configuration.registeredAcs(), context.clock().instant());
            return new Prepared(new OutboundAction(
                    actionId, OutboundKind.AUTHN_REQUEST, payload,
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
                var assertions = root.getElementsByTagNameNS(ASSERTION, "Assertion").getLength()
                        + root.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength();
                var success = codes.getLength() > 0
                        && SUCCESS.equals(((Element) codes.item(0)).getAttribute("Value"))
                        && assertions > 0;
                if (control) return success ? FixtureObservation.SATISFIED : FixtureObservation.CONTROL_FAILED;
                return success ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
            } catch (SamlException invalid) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", id, probe.name(), configuration.ssoEndpoint().toString(),
                    configuration.registeredAcs().toString(), "submillisecond-v1");
        }
    }
}
