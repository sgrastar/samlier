package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlIdentifierDeclarationCaseTest {
    private final SamlIdentifierDeclarationCase rule = new SamlIdentifierDeclarationCase();

    @Test
    void acceptsOneIdentifierDeclarationPerObject() {
        assertEquals(Outcome.SATISFIED, rule.evaluate(List.of(message("good", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="response-id">
                  <saml:Assertion ID="assertion-id"/>
                </samlp:Response>
                """))).outcome());
    }

    @Test
    void rejectsDuplicateIdValuesWithinOneDocument() {
        assertEquals(Outcome.VIOLATED, rule.evaluate(List.of(message("duplicate-value", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="same">
                  <saml:Assertion ID="same"/>
                </samlp:Response>
                """))).outcome());
    }

    @Test
    void detectsDuplicateIdAttributesBeforeTheNonValidatingParserRejectsTheDocument() {
        assertEquals(Outcome.VIOLATED, rule.evaluate(List.of(message("duplicate-attribute", """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="one" ID="two"/>
                """))).outcome());
    }

    @Test
    void quotedGreaterThanDoesNotTerminateTheRawStartTagScan() {
        assertEquals(Outcome.VIOLATED, rule.evaluate(List.of(message("quoted-greater-than", """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ProviderName="a > b" ID="one" ID="two"/>
                """))).outcome());
    }

    @Test
    void unrelatedMalformedXmlIsNotMisreportedAsAnIdentifierViolation() {
        assertEquals(Outcome.NOT_VERIFIED,
                rule.evaluate(List.of(message("malformed", "<samlp:AuthnRequest"))).outcome());
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
