package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.core.transcript.Direction;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.transcript.TranscriptEntry;
import com.samlscope.core.transcript.TranscriptInput;
import com.samlscope.core.transcript.TranscriptRecorder;

class LogoutTranscriptProfileCaseTest {
    @Test
    void detectsIdReuseOnlyForDifferentMessageObjects() {
        var fixture = fixture(
                inbound("a", request("_same", "2.0", ""), null),
                inbound("b", request("_same", "2.0", "<samlp:SessionIndex>x</samlp:SessionIndex>"), null));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(LogoutTranscriptProfileCase.Rule.UNIQUE_IDS));
        var retransmit = fixture(inbound("a", request("_same", "2.0", ""), null),
                inbound("b", request("_same", "2.0", ""), null));
        assertEquals(Outcome.SATISFIED, retransmit.evaluate(LogoutTranscriptProfileCase.Rule.UNIQUE_IDS));
    }

    @Test
    void correlatesResponseAndVersionRulesWithTheSuiteRequest() {
        var good = fixture(
                outbound("request", request("_request", "2.0", ""), null),
                inbound("response", response("_response", "2.0", "_request", success()), null));
        assertEquals(Outcome.SATISFIED, good.evaluate(LogoutTranscriptProfileCase.Rule.IN_RESPONSE_TO));
        assertEquals(Outcome.SATISFIED, good.evaluate(LogoutTranscriptProfileCase.Rule.RESPONSE_VERSION_CEILING));
        assertEquals(Outcome.SATISFIED, good.evaluate(LogoutTranscriptProfileCase.Rule.RESPONSE_VERSION_FLOOR));
        var wrong = fixture(
                outbound("request", request("_request", "2.0", ""), null),
                inbound("response", response("_response", "3.0", "_other", success()), null));
        assertEquals(Outcome.VIOLATED, wrong.evaluate(LogoutTranscriptProfileCase.Rule.IN_RESPONSE_TO));
    }

    @Test
    void consentRequiresXmlOrBindingSignatureAndTopStatusUsesOnlyTheCoreList() {
        var unsigned = fixture(inbound("response",
                response("_response", "2.0", "_request", success()).replace(
                        "Version=\"2.0\"", "Version=\"2.0\" Consent=\"urn:consent\""), null));
        assertEquals(Outcome.VIOLATED, unsigned.evaluate(LogoutTranscriptProfileCase.Rule.CONSENT_SIGNATURE));
        var redirectSigned = fixture(inbound("response",
                response("_response", "2.0", "_request", success()).replace(
                        "Version=\"2.0\"", "Version=\"2.0\" Consent=\"urn:consent\""), "Signature=abc"));
        assertEquals(Outcome.NOT_VERIFIED, redirectSigned.evaluate(LogoutTranscriptProfileCase.Rule.CONSENT_SIGNATURE));
        var secondaryAtTop = fixture(inbound("response",
                response("_response", "2.0", "_request", "urn:oasis:names:tc:SAML:2.0:status:PartialLogout"), null));
        assertEquals(Outcome.VIOLATED, secondaryAtTop.evaluate(LogoutTranscriptProfileCase.Rule.TOP_LEVEL_STATUS));
        var successAtTop = fixture(inbound("response",
                response("_response", "2.0", "_request", success()), null));
        assertEquals(Outcome.SATISFIED,
                successAtTop.evaluate(LogoutTranscriptProfileCase.Rule.TOP_LEVEL_STATUS));
    }

    @Test
    void asyncExtensionMustBeDirectlyInsideLogoutRequestExtensions() {
        var proper = fixture(inbound("request", request("_request", "2.0",
                "<samlp:Extensions><aslo:Asynchronous/></samlp:Extensions>"), null));
        assertEquals(Outcome.SATISFIED, proper.evaluate(LogoutTranscriptProfileCase.Rule.ASYNC_PLACEMENT));
        var misplaced = fixture(inbound("response",
                response("_response", "2.0", "_request", success()).replace(
                        "</samlp:LogoutResponse>", "<aslo:Asynchronous/></samlp:LogoutResponse>"), null));
        assertEquals(Outcome.VIOLATED, misplaced.evaluate(LogoutTranscriptProfileCase.Rule.ASYNC_PLACEMENT));
    }

    @Test
    void targetIssuedMessagesExposeIssuerSignatureAndUtcExpiryWithoutQuestionnaires() {
        var entity = "https://idp.example/entity";
        var request = request("_request", "2.0", "").replace(
                "<saml:NameID>",
                "<saml:Issuer Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:entity\">"
                        + entity + "</saml:Issuer><saml:NameID>").replace(
                "IssueInstant=\"2026-08-29T00:00:00Z\"",
                "IssueInstant=\"2026-08-29T00:00:00Z\" NotOnOrAfter=\"2026-08-29T00:05:00Z\"");
        var response = response("_response", "2.0", "_suite", success()).replace(
                "<samlp:Status>",
                "<saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
                        + entity + "</saml:Issuer><samlp:Status>");
        var fixture = fixture(inbound("request", request, null), inbound("response", response, null));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_COUNT, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_VALUE, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_FORMAT, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_COUNT, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_VALUE, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_FORMAT, entity));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER, entity));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_SIGNATURE, entity));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.RESPONSE_SIGNATURE, entity));
    }

    @Test
    void malformedIssuerAndNonUtcExpiryAreViolations() {
        var request = request("_request", "2.0", "").replace(
                "<saml:NameID>", "<saml:Issuer Format=\"wrong\">wrong</saml:Issuer><saml:NameID>")
                .replace("IssueInstant=\"2026-08-29T00:00:00Z\"",
                        "IssueInstant=\"2026-08-29T00:00:00Z\" NotOnOrAfter=\"2026-08-29T09:05:00+09:00\"");
        var fixture = fixture(inbound("request", request, null));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_VALUE, "https://idp.example/entity"));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_FORMAT, "https://idp.example/entity"));
        assertEquals(Outcome.VIOLATED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER, "https://idp.example/entity"));
    }

    @Test
    void targetLogoutRequestIsCorrelatedWithTheIssuedIdentifierAndSessionExpiry() {
        var assertion = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion><saml:Subject><saml:NameID
                    Format="urn:oasis:names:tc:SAML:2.0:nameid-format:persistent">user</saml:NameID>
                  </saml:Subject><saml:Conditions NotOnOrAfter="2026-08-29T00:05:00Z"/></saml:Assertion>
                </samlp:Response>
                """;
        var logout = request("_request", "2.0", "")
                .replace("<saml:NameID>", "<saml:NameID Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:persistent\">")
                .replace(
                "IssueInstant=\"2026-08-29T00:00:00Z\"",
                "IssueInstant=\"2026-08-29T00:00:00Z\" NotOnOrAfter=\"2026-08-29T00:05:00Z\"");
        var fixture = fixture(inbound("assertion", assertion, null), inbound("request", logout, null));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_IDENTIFIER_MATCH));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER_BOUND));

        var tooEarly = fixture(inbound("assertion", assertion, null), inbound("request", logout.replace(
                "NotOnOrAfter=\"2026-08-29T00:05:00Z\"",
                "NotOnOrAfter=\"2026-08-29T00:04:59Z\""), null));
        assertEquals(Outcome.VIOLATED, tooEarly.evaluate(
                LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER_BOUND));
    }

    @Test
    void redirectLogoutRequestIsAcceptedOnlyWhenACorrelatedSuccessReturns() {
        var fixture = fixture(
                outboundRedirect("request", request("_request", "2.0", ""), "SAMLRequest=abc"),
                inbound("response", response("_response", "2.0", "_request", success()), null));
        assertEquals(Outcome.SATISFIED, fixture.evaluate(
                LogoutTranscriptProfileCase.Rule.REDIRECT_LOGOUT_REQUEST_ACCEPTED));
    }

    private Fixture fixture(Entry... entries) { return new Fixture(List.of(entries)); }
    private Entry inbound(String id, String xml, String query) {
        return new Entry(id, Direction.INBOUND, "POST", xml, query);
    }
    private Entry outbound(String id, String xml, String query) {
        return new Entry(id, Direction.OUTBOUND, "POST", xml, query);
    }
    private Entry outboundRedirect(String id, String xml, String query) {
        return new Entry(id, Direction.OUTBOUND, "GET", xml, query);
    }

    private String request(String id, String version, String extra) {
        return "<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
                + "xmlns:aslo=\"urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo\" "
                + "ID=\"" + id + "\" Version=\"" + version + "\" IssueInstant=\"2026-08-29T00:00:00Z\">"
                + extra + "<saml:NameID>user</saml:NameID></samlp:LogoutRequest>";
    }

    private String response(String id, String version, String inResponseTo, String status) {
        return "<samlp:LogoutResponse xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:aslo=\"urn:oasis:names:tc:SAML:2.0:protocol:ext:async-slo\" "
                + "ID=\"" + id + "\" Version=\"" + version + "\" IssueInstant=\"2026-08-29T00:00:00Z\" "
                + "InResponseTo=\"" + inResponseTo + "\"><samlp:Status><samlp:StatusCode Value=\"" + status
                + "\"/></samlp:Status></samlp:LogoutResponse>";
    }
    private String success() { return "urn:oasis:names:tc:SAML:2.0:status:Success"; }

    private record Entry(String id, Direction direction, String method, String xml, String rawQuery) {}

    private static final class Fixture {
        private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
        private final List<TranscriptEntry> entries = new ArrayList<>();
        private final Map<String, byte[]> content = new HashMap<>();

        private Fixture(List<Entry> values) {
            var sequence = 0;
            for (var value : values) {
                var reference = "decoded-" + value.id();
                var bytes = value.xml().getBytes(StandardCharsets.UTF_8);
                entries.add(new TranscriptEntry(
                        value.id(), RUN, value.direction(), Instant.parse("2026-08-29T00:00:00Z").plusSeconds(sequence++),
                        "corr", value.method(), "https://suite.example/slo", 200, Map.of(), null, 0,
                        reference, bytes.length, "application/xml", value.rawQuery(), Map.of()));
                content.put(reference, bytes);
            }
        }

        private Outcome evaluate(LogoutTranscriptProfileCase.Rule rule) {
            return evaluate(rule, null);
        }

        private Outcome evaluate(LogoutTranscriptProfileCase.Rule rule, String entityId) {
            TranscriptRecorder recorder = new TranscriptRecorder() {
                @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
                @Override public TranscriptEntry updateSamlAnalysis(
                        String entryId, String correlationId, Map<String, Object> samlSummary) {
                    throw new UnsupportedOperationException();
                }
                @Override public List<TranscriptEntry> list(String runId) { return entries; }
            };
            TranscriptContentReader reader = entry -> content.get(entry.decodedSamlRef());
            return new LogoutTranscriptProfileCase(rule, List.of(), entityId)
                    .evaluate(RUN, recorder, reader).outcome();
        }
    }
}
