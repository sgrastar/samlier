package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlLexicalCaseTest {
    @Test
    void acceptsAbsoluteSamlUrisAndIgnoresXmlSignatureSameDocumentReferences() {
        var outcome = new SamlUriValueCase().evaluate(List.of(message("good", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                  Destination="https://sp.example/acs" IssueInstant="2026-08-29T00:00:00Z">
                  <saml:Issuer Format="urn:oasis:names:tc:SAML:2.0:nameid-format:entity">https://idp.example</saml:Issuer>
                  <saml:Assertion ID="a"><saml:Conditions><saml:AudienceRestriction>
                    <saml:Audience>https://sp.example/entity</saml:Audience>
                  </saml:AudienceRestriction></saml:Conditions></saml:Assertion>
                  <ds:Signature><ds:SignedInfo><ds:Reference URI="#a"/></ds:SignedInfo></ds:Signature>
                </samlp:Response>
                """)));

        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(3, outcome.details().get("observed_fields"));
    }

    @Test
    void rejectsRelativeBlankAndListMemberUris() {
        var protocol = message("protocol", """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  Destination="/sso" AssertionConsumerServiceURL=" "/>
                """);
        var metadata = message("metadata", """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="https://idp.example">
                  <md:IDPSSODescriptor protocolSupportEnumeration="urn:ok relative"/>
                </md:EntityDescriptor>
                """);

        var outcome = new SamlUriValueCase().evaluate(List.of(protocol, metadata));

        assertEquals(Outcome.VIOLATED, outcome.outcome());
        assertEquals(3, ((List<?>) outcome.details().get("violating_fields")).size());
    }

    @Test
    void requiresZuluRepresentationForEverySamlDateTimeField() {
        var good = new SamlTimeValueCase(SamlTimeValueCase.Rule.UTC_REPRESENTATION).evaluate(List.of(message("good", """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  IssueInstant="2026-08-29T00:00:00.123Z">
                  <saml:Conditions NotBefore="2026-08-29T00:00:00Z" NotOnOrAfter="2026-08-29T00:05:00Z"/>
                </saml:Assertion>
                """)));
        var bad = new SamlTimeValueCase(SamlTimeValueCase.Rule.UTC_REPRESENTATION).evaluate(List.of(message("bad", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  IssueInstant="2026-08-29T09:00:00+09:00"/>
                """)));

        assertEquals(Outcome.SATISFIED, good.outcome());
        assertEquals(Outcome.VIOLATED, bad.outcome());
    }

    @Test
    void rejectsLeapSecondsButNotOrdinarySeconds() {
        var rule = new SamlTimeValueCase(SamlTimeValueCase.Rule.NO_LEAP_SECOND);

        assertEquals(Outcome.SATISFIED, rule.evaluate(List.of(message("good", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  IssueInstant="2026-08-29T00:00:59Z"/>
                """))).outcome());
        assertEquals(Outcome.VIOLATED, rule.evaluate(List.of(message("bad", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  IssueInstant="2026-12-31T23:59:60Z"/>
                """))).outcome());
    }

    @Test
    void malformedOrIrrelevantMessagesDoNotBecomeFalsePassesOrViolations() {
        var rule = new SamlUriValueCase();

        assertEquals(Outcome.NOT_VERIFIED,
                rule.evaluate(List.of(message("malformed", "<samlp:Response"))).outcome());
        assertEquals(Outcome.SATISFIED_WITH_NOTE,
                rule.evaluate(List.of(message("irrelevant", """
                        <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"/>
                        """))).outcome());
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
