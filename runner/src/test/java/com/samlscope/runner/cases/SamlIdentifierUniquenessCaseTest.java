package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlIdentifierUniquenessCaseTest {
    @Test
    void allowsRetransmissionOfTheSameAuthnRequestObject() {
        var request = """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="request-1"/>
                """;
        var outcome = new SamlIdentifierUniquenessCase(
                SamlIdentifierUniquenessCase.Subject.SP_AUTHN_REQUEST)
                .evaluate(List.of(message("first", request), message("retry", request)));

        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(2, outcome.details().get("observed_assignments"));
    }

    @Test
    void rejectsReuseOfAnAuthnRequestIdForADifferentObject() {
        var first = """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="request-1" Destination="https://idp.example/one"/>
                """;
        var second = """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="request-1" Destination="https://idp.example/two"/>
                """;

        var outcome = new SamlIdentifierUniquenessCase(
                SamlIdentifierUniquenessCase.Subject.SP_AUTHN_REQUEST)
                .evaluate(List.of(message("first", first), message("second", second)));

        assertEquals(Outcome.VIOLATED, outcome.outcome());
        assertEquals(List.of("request-1"), outcome.details().get("colliding_ids"));
    }

    @Test
    void rejectsResponseAndAssertionSharingAnIdWithinOneDocument() {
        var response = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="same">
                  <saml:Assertion ID="same"/>
                </samlp:Response>
                """;

        var outcome = new SamlIdentifierUniquenessCase(
                SamlIdentifierUniquenessCase.Subject.IDP_RESPONSE_AND_ASSERTION)
                .evaluate(List.of(message("response", response)));

        assertEquals(Outcome.VIOLATED, outcome.outcome());
    }

    @Test
    void acceptsUniqueResponseAndAssertionIdsAcrossTransactions() {
        var one = response("r1", "a1");
        var two = response("r2", "a2");

        var outcome = new SamlIdentifierUniquenessCase(
                SamlIdentifierUniquenessCase.Subject.IDP_RESPONSE_AND_ASSERTION)
                .evaluate(List.of(message("one", one), message("two", two)));

        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(4, outcome.details().get("observed_assignments"));
    }

    private String response(String responseId, String assertionId) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="%s">
                  <saml:Assertion ID="%s"/>
                </samlp:Response>
                """.formatted(responseId, assertionId);
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
