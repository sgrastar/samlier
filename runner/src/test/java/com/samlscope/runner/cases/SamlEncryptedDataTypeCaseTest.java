package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlEncryptedDataTypeCaseTest {
    @Test
    void presenceRuleChecksAllThreeSamlEncryptedElements() {
        for (var localName : List.of("EncryptedAssertion", "EncryptedID", "EncryptedAttribute")) {
            assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_PRESENT, Outcome.SATISFIED,
                    encrypted(localName, "http://www.w3.org/2001/04/xmlenc#Element"));
            assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_PRESENT, Outcome.VIOLATED,
                    encrypted(localName, null));
        }
    }

    @Test
    void valueRuleRejectsContentButLeavesAbsenceToPresenceRule() {
        assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_IS_ELEMENT, Outcome.SATISFIED,
                encrypted("EncryptedAssertion", "http://www.w3.org/2001/04/xmlenc#Element"));
        assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_IS_ELEMENT, Outcome.VIOLATED,
                encrypted("EncryptedAssertion", "http://www.w3.org/2001/04/xmlenc#Content"));
        assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_IS_ELEMENT, Outcome.SATISFIED_WITH_NOTE,
                encrypted("EncryptedAssertion", null));
    }

    @Test
    void noEncryptedElementIsAnObservedOpportunityNote() {
        assertOutcome(SamlEncryptedDataTypeCase.Rule.TYPE_PRESENT, Outcome.SATISFIED_WITH_NOTE,
                "<saml:Assertion xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>");
    }

    private String encrypted(String localName, String type) {
        var typeAttribute = type == null ? "" : " Type=\"" + type + "\"";
        return "<saml:" + localName + " xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
                + "xmlns:xenc=\"http://www.w3.org/2001/04/xmlenc#\"><xenc:EncryptedData"
                + typeAttribute + "/></saml:" + localName + ">";
    }

    private void assertOutcome(SamlEncryptedDataTypeCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlEncryptedDataTypeCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
