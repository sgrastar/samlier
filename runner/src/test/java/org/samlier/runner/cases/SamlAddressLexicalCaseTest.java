package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlAddressLexicalCaseTest {
    @Test
    void acceptsDottedDecimalIpv4AndRfc3513Ipv6Forms() {
        assertOutcome(Outcome.SATISFIED, element("SubjectConfirmationData", "192.0.2.42"));
        assertOutcome(Outcome.SATISFIED, element("SubjectLocality", "2001:db8::1"));
        assertOutcome(Outcome.SATISFIED, element("SubjectLocality", "::ffff:192.0.2.42"));
    }

    @Test
    void rejectsNonDottedIpv4HostnamesAndInvalidLiterals() {
        assertOutcome(Outcome.VIOLATED, element("SubjectConfirmationData", "3221226026"));
        assertOutcome(Outcome.VIOLATED, element("SubjectLocality", "host.example"));
        assertOutcome(Outcome.VIOLATED, element("SubjectLocality", "2001:db8::1%en0"));
        assertOutcome(Outcome.VIOLATED, element("SubjectLocality", "300.0.0.1"));
    }

    @Test
    void omissionIsVacuouslySatisfiedWithANote() {
        assertOutcome(Outcome.SATISFIED_WITH_NOTE,
                "<saml:SubjectLocality xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>");
    }

    private String element(String localName, String address) {
        return "<saml:" + localName + " xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" Address=\""
                + address + "\"/>";
    }

    private void assertOutcome(Outcome expected, String xml) {
        var outcome = new SamlAddressLexicalCase().evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome());
    }
}
