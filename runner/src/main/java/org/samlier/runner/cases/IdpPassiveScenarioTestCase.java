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
import org.samlier.saml.normal.SamlErrorProbeRequestFactory;
import org.samlier.saml.normal.SamlErrorProbeRequestFactory.Probe;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Browser-assisted IsPassive scenarios; the operator performs login actions but never enters a verdict. */
public final class IdpPassiveScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final String PASSIVE_CASE = "IIP-IDP07-a-idp-01";
    public static final String FORCE_PASSIVE_CASE = "IIP-IDP06-c-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private final String id;
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlErrorProbeRequestFactory requests;

    public IdpPassiveScenarioTestCase(
            String id, java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(id, configurations, new SamlErrorProbeRequestFactory());
    }

    IdpPassiveScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlErrorProbeRequestFactory requests) {
        if (!List.of(PASSIVE_CASE, FORCE_PASSIVE_CASE).contains(id)) {
            throw new IllegalArgumentException("Unsupported passive case: " + id);
        }
        this.id = id;
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        var fixtures = id.equals(PASSIVE_CASE)
                ? List.<ScenarioFixture>of(
                        fixture("passive-without-session", Probe.PASSIVE_WITHOUT_SESSION,
                                Expectation.ANY_RESPONSE_WITHOUT_OPERATOR_INTERACTION, configuration),
                        fixture("establish-session", Probe.BASELINE_SUCCESS,
                                Expectation.SUCCESS_CONTROL, configuration),
                        fixture("passive-with-session", Probe.PASSIVE_WITH_SESSION,
                                Expectation.SUCCESS, configuration))
                : List.<ScenarioFixture>of(
                        fixture("force-authn-passive", Probe.FORCE_AUTHN_PASSIVE,
                                Expectation.ERROR_OR_UNOBSERVABLE_NONINTERACTIVE_SUCCESS, configuration));
        return new FixtureScenarioTestCase(
                id, TargetRole.IDP, fixtures,
                ignored -> configuration.preconditionsSatisfied(),
                new FixtureScenarioTestCase.Vocabulary(
                        "passive_preconditions_unmet", "idp.passive.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.passive.delivery-unknown",
                        "scenario_aborted", "idp.passive.aborted",
                        "case.idp.passive.control-failed",
                        "passive_interaction_violated", "case.idp.passive.violated",
                        "passive_result_not_conclusive", "idp.passive.inconclusive",
                        "case.idp.passive.inconclusive",
                        "idp.passive.satisfied", "case.idp.passive.satisfied"));
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public boolean requiresFreshSession(CaseState state) {
        return "passive-without-session".equals(state.data().get("fixture_id"))
                || "force-authn-passive".equals(state.data().get("fixture_id"));
    }
    @Override public boolean plansFreshSessionBoundary() { return true; }
    @Override public int plannedDeliberateActions() { return 1; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return "Start each passive fixture as instructed. Do not interact with the target during a passive step; "
                + "log in only during the session-establishment control. Samlier judges the correlated Response.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private ScenarioFixture fixture(
            String fixtureId, Probe probe, Expectation expectation,
            IdpErrorProbeConfiguration configuration) {
        return new PassiveFixture(fixtureId, probe, expectation, configuration, requests);
    }

    private enum Expectation {
        ANY_RESPONSE_WITHOUT_OPERATOR_INTERACTION,
        SUCCESS_CONTROL,
        SUCCESS,
        ERROR_OR_UNOBSERVABLE_NONINTERACTIVE_SUCCESS
    }

    private record PassiveFixture(
            String id,
            Probe probe,
            Expectation expectation,
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
                var document = SecureXml.parse(responseXml);
                var root = document.getDocumentElement();
                if (!PROTOCOL.equals(root.getNamespaceURI()) || !"Response".equals(root.getLocalName())
                        || !requestId.equals(root.getAttribute("InResponseTo"))) {
                    return FixtureObservation.NOT_VERIFIED;
                }
                var status = root.getElementsByTagNameNS(PROTOCOL, "StatusCode");
                if (status.getLength() == 0) return FixtureObservation.NOT_VERIFIED;
                var success = SUCCESS.equals(((Element) status.item(0)).getAttribute("Value"));
                var assertions = root.getElementsByTagNameNS(ASSERTION, "Assertion").getLength()
                        + root.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion").getLength();
                return switch (expectation) {
                    case ANY_RESPONSE_WITHOUT_OPERATOR_INTERACTION -> success && assertions == 0
                            ? FixtureObservation.NOT_VERIFIED : FixtureObservation.SATISFIED;
                    case SUCCESS_CONTROL -> success && assertions > 0
                            ? FixtureObservation.SATISFIED : FixtureObservation.CONTROL_FAILED;
                    case SUCCESS -> success && assertions > 0
                            ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
                    case ERROR_OR_UNOBSERVABLE_NONINTERACTIVE_SUCCESS -> success
                            ? FixtureObservation.NOT_VERIFIED : FixtureObservation.SATISFIED;
                };
            } catch (SamlException malformed) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", id, probe.name(), expectation.name(),
                    configuration.ssoEndpoint().toString(), configuration.registeredAcs().toString());
        }
    }
}
