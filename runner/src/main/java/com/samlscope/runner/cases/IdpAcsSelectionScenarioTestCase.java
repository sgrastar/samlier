package com.samlscope.runner.cases;

import java.net.URI;
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
import com.samlscope.saml.normal.SamlAcsSelectionRequestFactory;
import com.samlscope.saml.normal.SamlAcsSelectionRequestFactory.Fixture;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Browser-assisted, Transcript-judged ACS selection scenarios for a target IdP. */
public final class IdpAcsSelectionScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final String INDEX_CASE = "IIP-IDP12-a-idp-01";
    public static final String URL_CASE = "IIP-IDP12-e-idp-01";
    public static final String BINDING_CASE = "IIP-IDP12-f-idp-01";
    public static final String UNREGISTERED_URL_CASE = "IIP-IDP12-b-idp-01";
    public static final String UNKNOWN_INDEX_CASE = "IIP-IDP12-d-idp-01";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private final String id;
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlAcsSelectionRequestFactory requests;

    public IdpAcsSelectionScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(id, configurations, new SamlAcsSelectionRequestFactory());
    }

    IdpAcsSelectionScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlAcsSelectionRequestFactory requests) {
        this.id = requireSupported(id);
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        var defaultAcs = configuration.registeredAcs();
        var secondaryAcs = defaultAcs.resolve("1");
        var fixtures = switch (id) {
            case INDEX_CASE -> List.<ScenarioFixture>of(
                    fixture("default-control", Fixture.DEFAULT, Expectation.SUCCESS_AT_DEFAULT,
                            configuration, defaultAcs, secondaryAcs),
                    fixture("non-default-index", Fixture.INDEX_ONE, Expectation.SUCCESS_AT_SECONDARY,
                            configuration, defaultAcs, secondaryAcs));
            case URL_CASE -> List.<ScenarioFixture>of(
                    fixture("default-control", Fixture.DEFAULT, Expectation.SUCCESS_AT_DEFAULT,
                            configuration, defaultAcs, secondaryAcs),
                    fixture("non-default-url", Fixture.URL_ONE, Expectation.SUCCESS_AT_SECONDARY,
                            configuration, defaultAcs, secondaryAcs));
            case BINDING_CASE -> List.<ScenarioFixture>of(
                    fixture("unsupported-binding", Fixture.UNSUPPORTED_BINDING, Expectation.ERROR_AT_DEFAULT,
                            configuration, defaultAcs, secondaryAcs));
            case UNREGISTERED_URL_CASE -> List.<ScenarioFixture>of(
                    fixture("registered-url-control", Fixture.URL_ONE, Expectation.SUCCESS_AT_SECONDARY,
                            configuration, defaultAcs, secondaryAcs),
                    fixture("unregistered-url", Fixture.UNKNOWN_URL, Expectation.DEFAULT_OR_ERROR,
                            configuration, defaultAcs, secondaryAcs));
            case UNKNOWN_INDEX_CASE -> List.<ScenarioFixture>of(
                    fixture("unknown-index", Fixture.UNKNOWN_INDEX, Expectation.DEFAULT_OR_ERROR,
                            configuration, defaultAcs, secondaryAcs));
            default -> throw new IllegalStateException("Unsupported ACS scenario");
        };
        return new FixtureScenarioTestCase(
                id, TargetRole.IDP, fixtures,
                ignored -> configuration.userAgentAvailable() && configuration.acceptableResponseLocationKnown(),
                new FixtureScenarioTestCase.Vocabulary(
                        "acs_probe_preconditions_unmet", "idp.acs-probe.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.acs-probe.delivery-unknown",
                        "scenario_aborted", "idp.acs-probe.aborted",
                        "case.idp.acs-probe.control-failed",
                        "idp_acs_selection_violated", "case.idp.acs-probe.violated",
                        "idp_acs_selection_not_conclusive", "idp.acs-probe.inconclusive",
                        "case.idp.acs-probe.inconclusive",
                        "idp.acs-probe.satisfied", "case.idp.acs-probe.satisfied"));
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return "Start the ACS selection scenario and log in when the target asks. SAMLscope compares the correlated "
                + "Response destination and Status with the requested index, URL, or binding; do not enter a verdict.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private ScenarioFixture fixture(
            String fixtureId,
            Fixture fixture,
            Expectation expectation,
            IdpErrorProbeConfiguration configuration,
            URI defaultAcs,
            URI secondaryAcs) {
        return new AcsFixture(
                fixtureId, fixture, expectation, configuration, defaultAcs, secondaryAcs, requests);
    }

    private enum Expectation { SUCCESS_AT_DEFAULT, SUCCESS_AT_SECONDARY, ERROR_AT_DEFAULT, DEFAULT_OR_ERROR }

    private record AcsFixture(
            String id,
            Fixture fixture,
            Expectation expectation,
            IdpErrorProbeConfiguration configuration,
            URI defaultAcs,
            URI secondaryAcs,
            SamlAcsSelectionRequestFactory requests) implements ScenarioFixture {
        @Override
        public Prepared prepare(CaseContext context, String actionId) {
            var requestId = "_" + actionId;
            var payload = requests.build(
                    fixture, requestId, configuration.ssoEndpoint(), configuration.suiteIssuer(),
                    defaultAcs, secondaryAcs, context.clock().instant());
            return new Prepared(new OutboundAction(
                    actionId, OutboundKind.AUTHN_REQUEST, payload,
                    configuration.ssoEndpoint(), false), requestId);
        }

        @Override
        public FixtureObservation observe(String requestId, byte[] responseXml) {
            try {
                var document = SecureXml.parse(responseXml);
                var root = document.getDocumentElement();
                if (!PROTOCOL.equals(root.getNamespaceURI()) || !"Response".equals(root.getLocalName())
                        || !requestId.equals(root.getAttribute("InResponseTo"))) {
                    return FixtureObservation.NOT_VERIFIED;
                }
                var statusCodes = root.getElementsByTagNameNS(PROTOCOL, "StatusCode");
                if (statusCodes.getLength() == 0) return FixtureObservation.NOT_VERIFIED;
                var success = SUCCESS.equals(((Element) statusCodes.item(0)).getAttribute("Value"));
                var expectedDestination = expectation == Expectation.SUCCESS_AT_SECONDARY
                        ? secondaryAcs : defaultAcs;
                var destinationMatches = expectedDestination.toString().equals(root.getAttribute("Destination"));
                return switch (expectation) {
                    case SUCCESS_AT_DEFAULT -> success && destinationMatches
                            ? FixtureObservation.SATISFIED
                            : FixtureObservation.CONTROL_FAILED;
                    case SUCCESS_AT_SECONDARY -> success && destinationMatches
                            ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
                    case ERROR_AT_DEFAULT -> !success && destinationMatches
                            ? FixtureObservation.SATISFIED
                            : success ? FixtureObservation.NOT_VERIFIED : FixtureObservation.VIOLATED;
                    case DEFAULT_OR_ERROR -> destinationMatches
                            ? FixtureObservation.SATISFIED : FixtureObservation.VIOLATED;
                };
            } catch (SamlException malformed) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", id, fixture.name(), expectation.name(),
                    configuration.ssoEndpoint().toString(), defaultAcs.toString(), secondaryAcs.toString());
        }
    }

    private static String requireSupported(String value) {
        if (!List.of(INDEX_CASE, URL_CASE, BINDING_CASE, UNREGISTERED_URL_CASE, UNKNOWN_INDEX_CASE)
                .contains(value)) {
            throw new IllegalArgumentException("Unsupported ACS selection case: " + value);
        }
        return value;
    }
}
