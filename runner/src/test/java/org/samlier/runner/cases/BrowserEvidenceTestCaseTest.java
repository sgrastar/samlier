package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.DefaultCaseContext;
import org.samlier.saml.normal.SecureXml;

class BrowserEvidenceTestCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-29T13:00:00Z");
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";

    @Test
    void requiresBrowserCompletionBeforeAcceptingARestrictedEvidenceAnswer() {
        var evidence = new AttestedOutcomeTestCase(
                "IIP-G01-a-idp-01", TargetRole.IDP, "browser.evidence", "Review browser evidence.",
                Duration.ofHours(1), List.of(
                        AttestationOption.of("satisfied", Outcome.SATISFIED, "satisfied"),
                        AttestationOption.notVerified("unclear", "unclear", "evidence_unavailable")));
        var testCase = new BrowserEvidenceTestCase(
                evidence, URI.create("https://suite.example"), "Execute both controls.", Duration.ofHours(1));

        var browser = assertInstanceOf(CaseStep.AwaitBrowser.class, testCase.start(context()));
        assertEquals(URI.create("https://suite.example/browser/" + RUN_ID + "/IIP-G01-a-idp-01"),
                browser.startUrl());
        assertThrows(IllegalArgumentException.class, () -> testCase.resume(
                context(), browser.next(), new CaseEvent.Attested("satisfied", "too early")));

        var attestation = assertInstanceOf(CaseStep.AwaitAttestation.class, testCase.resume(
                context(), browser.next(), new CaseEvent.BrowserReturned("operator-completed-approved-steps")));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), attestation.next(), new CaseEvent.Attested("satisfied", "Transcript entry 12")));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
        assertThrows(IllegalArgumentException.class, () -> testCase.resume(
                context(), attestation.next(), new CaseEvent.Attested("PASS", "client verdict")));
    }

    @Test
    void ordinaryTranscriptFinishesSupportedBrowserCaseWithoutHumanCompletion() {
        var fallback = browserCase("IIP-SSO03-a-idp-01");
        var xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="2.0">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8);
        var entry = new TranscriptEntry(
                "entry-1", RUN_ID, org.samlier.core.transcript.Direction.INBOUND, NOW, "_response",
                "POST", "https://suite.example/acs", 200, Map.of(), null, 0,
                "decoded-ref", xml.length, "application/x-www-form-urlencoded", null,
                Map.of("type", "Response", "normalFlowAccepted", true));
        var testCase = new AutoBrowserEvidenceTestCase(fallback, ignored -> xml);

        var finish = assertInstanceOf(CaseStep.Finish.class,
                testCase.start(context(new FixedTranscript(List.of(entry)))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void inconclusiveOrdinaryTranscriptWaitsForMoreProtocolEvidence() {
        var fallback = browserCase("IIP-SSO03-a-idp-01");
        var testCase = new AutoBrowserEvidenceTestCase(fallback, ignored -> new byte[0]);

        assertInstanceOf(CaseStep.AwaitBrowser.class,
                testCase.start(context(new FixedTranscript(List.of()))));
    }

    @Test
    void waitingCaseFinishesWhenTheSuiteSignalsConclusiveTranscriptEvidence() {
        var fallback = browserCase("IIP-SSO03-a-idp-01");
        var xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="2.0">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8);
        var testCase = new AutoBrowserEvidenceTestCase(fallback, ignored -> xml);
        var waiting = assertInstanceOf(CaseStep.AwaitBrowser.class,
                testCase.start(context(new FixedTranscript(List.of()))));
        var entry = new TranscriptEntry(
                "entry-ready", RUN_ID, org.samlier.core.transcript.Direction.INBOUND, NOW, "_response",
                "POST", "https://suite.example/acs", 200, Map.of(), null, 0,
                "decoded-ref", xml.length, "application/x-www-form-urlencoded", null,
                Map.of("type", "Response", "normalFlowAccepted", true));

        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(new FixedTranscript(List.of(entry))), waiting.next(),
                new CaseEvent.TranscriptReady()));

        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void inspectsAnEncryptedAssertionThroughTheRunScopedKeyWithoutPersistingPlaintext() {
        var fallback = browserCase("IIP-SSO05-a2-idp-01");
        var response = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Version="2.0">
                  <saml:EncryptedAssertion><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"/></saml:EncryptedAssertion>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8);
        var assertion = """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Version="2.0">
                  <saml:Subject><saml:NameID Format="urn:oasis:names:tc:SAML:2.0:nameid-format:persistent">opaque-123</saml:NameID></saml:Subject>
                </saml:Assertion>
                """.getBytes(StandardCharsets.UTF_8);
        var entry = new TranscriptEntry(
                "entry-encrypted", RUN_ID, org.samlier.core.transcript.Direction.INBOUND, NOW, "_response",
                "POST", "https://suite.example/acs", 200, Map.of(), null, 0,
                "decoded-ref", response.length, "application/x-www-form-urlencoded", null,
                Map.of("type", "Response", "normalFlowAccepted", true));
        var key = new PrivateKey() {
            @Override public String getAlgorithm() { return "test"; }
            @Override public String getFormat() { return "test"; }
            @Override public byte[] getEncoded() { return new byte[0]; }
        };
        var testCase = new AutoBrowserEvidenceTestCase(
                fallback, ignored -> response, ignored -> java.util.Optional.of(key),
                ignored -> java.util.Optional.empty(),
                (wrapper, ignored) -> SecureXml.parse(assertion).getDocumentElement());

        var finish = assertInstanceOf(CaseStep.Finish.class,
                testCase.start(context(new FixedTranscript(List.of(entry)))));

        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void durableButNotYetParsedInboundResponseCannotCompleteTheCase() {
        var fallback = browserCase("IIP-SSO03-a-idp-01");
        var xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="2.0">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8);
        var unparsed = new TranscriptEntry(
                "entry-unparsed", RUN_ID, org.samlier.core.transcript.Direction.INBOUND, NOW, RUN_ID,
                "POST", "https://suite.example/acs", 200, Map.of(), null, 0,
                "decoded-ref", xml.length, "application/x-www-form-urlencoded", null,
                Map.of("type", "SAMLResponse", "parseStatus", "not-yet-parsed"));
        var testCase = new AutoBrowserEvidenceTestCase(fallback, ignored -> xml);

        assertInstanceOf(CaseStep.AwaitBrowser.class,
                testCase.start(context(new FixedTranscript(List.of(unparsed)))));
    }

    @Test
    void parsedButRejectedResponseCannotCompleteTheCase() {
        var fallback = browserCase("IIP-SSO03-a-idp-01");
        var xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="2.0">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8);
        var rejected = new TranscriptEntry(
                "entry-rejected", RUN_ID, org.samlier.core.transcript.Direction.INBOUND, NOW, "_wrong",
                "POST", "https://suite.example/acs", 200, Map.of(), null, 0,
                "decoded-ref", xml.length, "application/x-www-form-urlencoded", null,
                Map.of("type", "Response", "normalFlowAccepted", false));
        var testCase = new AutoBrowserEvidenceTestCase(fallback, ignored -> xml);

        assertInstanceOf(CaseStep.AwaitBrowser.class,
                testCase.start(context(new FixedTranscript(List.of(rejected)))));
    }

    private BrowserEvidenceTestCase browserCase(String id) {
        var evidence = new AttestedOutcomeTestCase(
                id, TargetRole.IDP, "browser.evidence", "Review browser evidence.",
                Duration.ofHours(1), List.of(
                        AttestationOption.of("satisfied", Outcome.SATISFIED, "satisfied"),
                        AttestationOption.notVerified("unclear", "unclear", "evidence_unavailable")));
        return new BrowserEvidenceTestCase(
                evidence, URI.create("https://suite.example"), "Execute the approved controls.", Duration.ofHours(1));
    }

    private DefaultCaseContext context() {
        return context(new NoopTranscript());
    }

    private DefaultCaseContext context(TranscriptRecorder recorder) {
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Browser", PlanProfile.IDP_CORE,
                new TestPlan.Target(
                        TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                new TestPlan.Interaction(true, true), NOW, NOW);
        return new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(), plan.interaction(),
                Reachability.CONFIRMED, recorder, true);
    }

    private static final class NoopTranscript implements TranscriptRecorder {
        @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> summary) {
            throw new UnsupportedOperationException();
        }
        @Override public List<TranscriptEntry> list(String runId) { return List.of(); }
    }

    private static final class FixedTranscript implements TranscriptRecorder {
        private final List<TranscriptEntry> entries;

        private FixedTranscript(List<TranscriptEntry> entries) { this.entries = List.copyOf(entries); }
        @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> summary) {
            throw new UnsupportedOperationException();
        }
        @Override public List<TranscriptEntry> list(String runId) { return entries; }
    }
}
