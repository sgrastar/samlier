package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlQualifierOmissionCaseTest {
    @Test
    void entityIssuerAndUnspecifiedNameIdOmitQualifiers() {
        assertOutcome(Outcome.SATISFIED, issuer("urn:oasis:names:tc:SAML:2.0:nameid-format:entity", ""));
        assertOutcome(Outcome.VIOLATED, issuer(
                "urn:oasis:names:tc:SAML:2.0:nameid-format:entity", " NameQualifier=\"example\""));
        assertOutcome(Outcome.VIOLATED, issuer(null, " NameQualifier=\"example\""));
        assertOutcome(Outcome.VIOLATED, nameId(
                "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified", " SPNameQualifier=\"example\""));
        assertOutcome(Outcome.VIOLATED, nameId(null, " NameQualifier=\"example\""));
        assertOutcome(Outcome.VIOLATED, """
                <saml:BaseID xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" NameQualifier="example">id</saml:BaseID>
                """);
    }

    @Test
    void formatsThatDefineQualifierSemanticsAreControlsNotViolations() {
        assertOutcome(Outcome.SATISFIED_WITH_NOTE, nameId(
                "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent", " NameQualifier=\"idp\" SPNameQualifier=\"sp\""));
        assertOutcome(Outcome.SATISFIED_WITH_NOTE, nameId(
                "urn:oasis:names:tc:SAML:2.0:nameid-format:transient", " NameQualifier=\"idp\""));
    }

    @Test
    void issuerWithoutQualifiersSatisfiesTheRuleWhenFormatIsOmitted() {
        assertOutcome(Outcome.SATISFIED, issuer(null, ""));
    }

    private String issuer(String format, String qualifiers) {
        var formatAttribute = format == null ? "" : " Format=\"" + format + "\"";
        return "<saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\""
                + formatAttribute + qualifiers + ">issuer</saml:Issuer>";
    }

    private String nameId(String format, String qualifiers) {
        var formatAttribute = format == null ? "" : " Format=\"" + format + "\"";
        return "<saml:NameID xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\""
                + formatAttribute + qualifiers + ">subject</saml:NameID>";
    }

    private void assertOutcome(Outcome expected, String xml) {
        var outcome = new SamlQualifierOmissionCase().evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome());
    }
}
