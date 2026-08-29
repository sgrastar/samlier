package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;

class EcpTranscriptProfileCaseTest {
    @Test
    void basicExchangeRequiresAnObservedRequestAndSoapCompletion() {
        assertEquals(Outcome.NOT_VERIFIED, fixture(request()).evaluate(
                EcpTranscriptProfileCase.Rule.BASIC_EXCHANGE));
        assertEquals(Outcome.SATISFIED, fixture(request(), response(true, true, true)).evaluate(
                EcpTranscriptProfileCase.Rule.BASIC_EXCHANGE));
    }

    @Test
    void responseHeaderAndBearerRecipientAreBoundToTheRequestedPaosConsumer() {
        var valid = fixture(request(), response(true, true, true));
        assertEquals(Outcome.SATISFIED, valid.evaluate(EcpTranscriptProfileCase.Rule.RESPONSE_HEADER));
        assertEquals(Outcome.SATISFIED, valid.evaluate(EcpTranscriptProfileCase.Rule.BEARER_CONFIRMATION));
        var invalid = fixture(request(), response(false, true, true));
        assertEquals(Outcome.VIOLATED, invalid.evaluate(EcpTranscriptProfileCase.Rule.RESPONSE_HEADER));
        assertEquals(Outcome.VIOLATED, invalid.evaluate(EcpTranscriptProfileCase.Rule.BEARER_CONFIRMATION));
    }

    @Test
    void channelBindingsMustAppearInBothRequiredOutputLocations() {
        assertEquals(Outcome.SATISFIED, fixture(request(), response(true, true, true)).evaluate(
                EcpTranscriptProfileCase.Rule.CHANNEL_BINDINGS));
        assertEquals(Outcome.VIOLATED, fixture(request(), response(true, false, true)).evaluate(
                EcpTranscriptProfileCase.Rule.CHANNEL_BINDINGS));
    }

    @Test
    void samlEcRequiresBothCopiesSufficientKeyMaterialAndEncryptedAssertion() {
        assertEquals(Outcome.SATISFIED, fixture(request(), response(true, true, true)).evaluate(
                EcpTranscriptProfileCase.Rule.GENERATED_KEY));
        assertEquals(Outcome.VIOLATED, fixture(request(), response(true, true, false)).evaluate(
                EcpTranscriptProfileCase.Rule.GENERATED_KEY));
    }

    private Fixture fixture(Soap... values) { return new Fixture(List.of(values)); }

    private Soap request() {
        return new Soap("request", Direction.OUTBOUND, """
                <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:paos="urn:liberty:paos:2003-08"
                    xmlns:cb="urn:oasis:names:tc:SAML:protocol:ext:channel-binding">
                  <S:Header>
                    <paos:Request responseConsumerURL="https://suite.example/paos"/>
                    <cb:ChannelBindings>binding</cb:ChannelBindings>
                  </S:Header>
                  <S:Body><request/></S:Body>
                </S:Envelope>
                """, Map.of("Authorization", List.of("<redacted: Basic, 20 bytes>")));
    }

    private Soap response(boolean correctRecipient, boolean headerBinding, boolean encrypted) {
        var recipient = correctRecipient ? "https://suite.example/paos" : "https://other.example/paos";
        var headerCb = headerBinding
                ? "<cb:ChannelBindings S:actor=\"http://schemas.xmlsoap.org/soap/actor/next\" S:mustUnderstand=\"1\">binding</cb:ChannelBindings>"
                : "";
        var assertion = encrypted
                ? "<saml:EncryptedAssertion/><saml:Assertion ID=\"_a\"><saml:Advice><cb:ChannelBindings>binding</cb:ChannelBindings><samlec:GeneratedKey>AAECAwQFBgcICQoLDA0ODw==</samlec:GeneratedKey></saml:Advice><saml:Subject><saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\"><saml:SubjectConfirmationData Recipient=\"" + recipient + "\"/></saml:SubjectConfirmation></saml:Subject></saml:Assertion>"
                : "<saml:Assertion><saml:Advice><samlec:GeneratedKey>AA==</samlec:GeneratedKey></saml:Advice></saml:Assertion>";
        return new Soap("response", Direction.INBOUND, """
                <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:ecp="urn:oasis:names:tc:SAML:2.0:profiles:SSO:ecp"
                    xmlns:cb="urn:oasis:names:tc:SAML:protocol:ext:channel-binding"
                    xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                    xmlns:samlec="urn:ietf:params:xml:ns:samlec">
                  <S:Header>
                    <ecp:Response AssertionConsumerServiceURL="%s" S:actor="http://schemas.xmlsoap.org/soap/actor/next" S:mustUnderstand="1"/>
                    %s
                    <samlec:GeneratedKey>AAECAwQFBgcICQoLDA0ODw==</samlec:GeneratedKey>
                  </S:Header>
                  <S:Body><samlp:Response>%s</samlp:Response></S:Body>
                </S:Envelope>
                """.formatted(recipient, headerCb, assertion), Map.of());
    }

    private record Soap(String id, Direction direction, String xml, Map<String, List<String>> headers) {}

    private static final class Fixture {
        private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
        private final List<TranscriptEntry> entries;
        private final Map<String, byte[]> content;

        private Fixture(List<Soap> values) {
            var rows = new java.util.ArrayList<TranscriptEntry>();
            var blobs = new HashMap<String, byte[]>();
            var sequence = 0;
            for (var value : values) {
                var reference = "decoded-" + value.id();
                var bytes = value.xml().getBytes(StandardCharsets.UTF_8);
                rows.add(new TranscriptEntry(
                        value.id(), RUN, value.direction(), Instant.parse("2026-08-29T00:00:00Z").plusSeconds(sequence++),
                        "corr", "POST", "https://idp.example/ecp", 200, value.headers(), null, 0,
                        reference, bytes.length, "application/soap+xml", null, Map.of()));
                blobs.put(reference, bytes);
            }
            entries = List.copyOf(rows); content = Map.copyOf(blobs);
        }

        private Outcome evaluate(EcpTranscriptProfileCase.Rule rule) {
            TranscriptRecorder recorder = new TranscriptRecorder() {
                @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
                @Override public TranscriptEntry updateSamlAnalysis(
                        String entryId, String correlationId, Map<String, Object> samlSummary) {
                    throw new UnsupportedOperationException();
                }
                @Override public List<TranscriptEntry> list(String runId) { return entries; }
            };
            TranscriptContentReader reader = entry -> content.get(entry.decodedSamlRef());
            return new EcpTranscriptProfileCase(rule, List.of()).evaluate(RUN, recorder, reader).outcome();
        }
    }
}
