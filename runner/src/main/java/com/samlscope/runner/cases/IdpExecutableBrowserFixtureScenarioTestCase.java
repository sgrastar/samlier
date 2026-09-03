package com.samlscope.runner.cases;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

/**
 * Replaces instruction-only browser completion with real, correlated protocol fixtures. These
 * cases intentionally remain NOT_VERIFIED when the approved obligation contains variants that a
 * remote IdP cannot expose through SAML traffic alone; executing a partial fixture must never be
 * promoted into a false conformance claim.
 */
public final class IdpExecutableBrowserFixtureScenarioTestCase
        implements TestCase, BrowserFrontChannelScenario, BrowserPrompt {
    public static final List<String> CASE_IDS = List.of(
            "IIP-EXT01-b-idp-01", "IIP-EXT01-c-idp-01", "IIP-G01-a-idp-01",
            "IIP-G02-a-idp-01", "IIP-G03-b-idp-01", "IIP-IDP12-c-idp-01",
            "IIP-MD05-f5-idp-01", "IIP-MD05-fg-idp-01", "IIP-SSO01-eb-idp-01",
            "IIP-SSO01-i2-idp-01", "IIP-SSO04-a-idp-01", "IIP-SSO05-a3-idp-01",
            "IIP-SSO07-b-idp-01");

    private final String id;
    private final java.util.function.Function<String, IdpErrorProbeConfiguration> configurations;
    private final SamlErrorProbeRequestFactory requests;

    public IdpExecutableBrowserFixtureScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations) {
        this(id, configurations, new SamlErrorProbeRequestFactory());
    }

    IdpExecutableBrowserFixtureScenarioTestCase(
            String id,
            java.util.function.Function<String, IdpErrorProbeConfiguration> configurations,
            SamlErrorProbeRequestFactory requests) {
        if (!CASE_IDS.contains(id)) throw new IllegalArgumentException("Unsupported executable browser case: " + id);
        this.id = id;
        this.configurations = java.util.Objects.requireNonNull(configurations, "configurations");
        this.requests = java.util.Objects.requireNonNull(requests, "requests");
    }

    private FixtureScenarioTestCase scenario(String runId) {
        var configuration = java.util.Objects.requireNonNull(configurations.apply(runId));
        var fixtures = probes().stream()
                .map(probe -> (ScenarioFixture) new PartialFixture(
                        probe.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                        probe, configuration, requests, "IIP-G01-a-idp-01".equals(id)))
                .toList();
        return new FixtureScenarioTestCase(
                id, TargetRole.IDP, fixtures,
                ignored -> configuration.userAgentAvailable()
                        && configuration.acceptableResponseLocationKnown(),
                new FixtureScenarioTestCase.Vocabulary(
                        "browser_fixture_preconditions_unmet", "idp.browser-fixture.preconditions-unmet",
                        "delivery_or_response_unknown", "idp.browser-fixture.delivery-unknown",
                        "scenario_aborted", "idp.browser-fixture.aborted",
                        "case.idp.browser-fixture.control-failed",
                        "browser_fixture_observed_violation", "case.idp.browser-fixture.violated",
                        "additional_variants_not_externally_observable", "browser_fixture_partial",
                        "case.idp.browser-fixture.partial",
                        "browser_fixture_satisfied", "case.idp.browser-fixture.satisfied"));
    }

    private List<Probe> probes() {
        return switch (id) {
            case "IIP-EXT01-b-idp-01" -> List.of(Probe.UNKNOWN_EXTENSION);
            case "IIP-EXT01-c-idp-01" -> List.of(Probe.UNKNOWN_ANY_ATTRIBUTE);
            case "IIP-G01-a-idp-01" -> List.of(Probe.BASELINE_SUCCESS);
            case "IIP-G02-a-idp-01" -> List.of(Probe.STRING_BOUNDARY_255, Probe.STRING_BOUNDARY_256);
            case "IIP-G03-b-idp-01" -> List.of(
                    Probe.DTD_AUTHN_REQUEST, Probe.DTD_EXTERNAL_ENTITY_AUTHN_REQUEST);
            case "IIP-IDP12-c-idp-01" -> List.of(Probe.ACS_SELECTION_OMITTED);
            case "IIP-SSO01-eb-idp-01" -> List.of(
                    Probe.BASELINE_SUCCESS, Probe.ISSUER_TRAILING_WHITESPACE);
            case "IIP-SSO05-a3-idp-01" -> List.of(Probe.PERSISTENT_NAMEID_POLICY);
            case "IIP-SSO07-b-idp-01" -> List.of(Probe.UNRECOGNIZED_SUBJECT);
            default -> List.of(Probe.BASELINE_SUCCESS);
        };
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }
    @Override public CaseStep start(CaseContext context) { return scenario(context.runId()).start(context); }
    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return scenario(context.runId()).resume(context, state, event);
    }
    @Override public String browserInstructionsEn() {
        return "Run the correlated SAML fixtures for this case. SAMLscope records observable protocol behavior "
                + "and conservatively leaves variants that are not externally provable as not verified; do not enter a verdict.";
    }
    @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }

    private record PartialFixture(
            String id,
            Probe probe,
            IdpErrorProbeConfiguration configuration,
            SamlErrorProbeRequestFactory requests,
            boolean useAttestedClockTolerance) implements ScenarioFixture {
        @Override public Prepared prepare(CaseContext context, String actionId) {
            var requestId = "_" + actionId;
            var issueInstant = context.clock().instant();
            if (useAttestedClockTolerance) {
                issueInstant = issueInstant.minusSeconds(
                        Math.max(0, context.parameters().clockSkewToleranceSeconds() - 1L));
            }
            var payload = requests.build(
                    probe, requestId, configuration.ssoEndpoint(), configuration.suiteIssuer(),
                    configuration.registeredAcs(), issueInstant);
            return new Prepared(new OutboundAction(
                    actionId, OutboundKind.AUTHN_REQUEST, payload, configuration.ssoEndpoint(), false), requestId);
        }

        @Override public FixtureObservation observe(String requestId, byte[] responseXml) {
            try {
                var root = SecureXml.parse(responseXml).getDocumentElement();
                if (!"urn:oasis:names:tc:SAML:2.0:protocol".equals(root.getNamespaceURI())
                        || !"Response".equals(root.getLocalName())
                        || !requestId.equals(root.getAttribute("InResponseTo"))) {
                    return FixtureObservation.NOT_VERIFIED;
                }
                // A correlated response proves that this concrete input was exercised. The case
                // still contains approved variants not exposed by this single remote interface.
                return FixtureObservation.NOT_VERIFIED;
            } catch (SamlException malformed) {
                return FixtureObservation.NOT_VERIFIED;
            }
        }

        @Override public Duration timeout() { return configuration.responseTimeout(); }
        @Override public String definitionKey() {
            return String.join("|", id, probe.name(), configuration.ssoEndpoint().toString(),
                    configuration.registeredAcs().toString(), Boolean.toString(useAttestedClockTolerance));
        }
    }
}
