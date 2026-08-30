package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class NormalFlowBrowserObservationTest {
    @Test
    void autoCompletesOnlyTheApprovedNormalFlowCases() {
        assertTrue(NormalFlowBrowserObservation.supports("IIP-SSO03-a-idp-01"));
        assertTrue(!NormalFlowBrowserObservation.supports("IIP-SSO01-g-idp-01"));
    }

    @Test
    void successfulPostResponseProvesPostResponseSupport() {
        assertOutcome("IIP-SSO03-a-idp-01", Outcome.SATISFIED,
                response("POST", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
        assertEmpty("IIP-SSO03-a-idp-01",
                response("GET", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
    }

    @Test
    void redirectDeliveryIsRejectedButPostAloneDoesNotStandInForTheRedirectAcsControl() {
        assertEmpty("IIP-SSO01-x-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
        assertOutcome("IIP-SSO01-x-idp-01", Outcome.VIOLATED,
                response("GET", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
    }

    @Test
    void tlsUsesTheUrlsOfBothActualExchanges() {
        var request = request("https://idp.example/sso", "2.0", "_request");
        var response = response("POST", "https://suite.example/acs", "2.0", "_request",
                assertion("issuer", null, null));
        assertOutcome("IIP-SSO01-ad-idp-01", Outcome.SATISFIED, request, response);
        assertOutcome("IIP-SSO01-ad-idp-01", Outcome.VIOLATED,
                request("http://idp.example/sso", "2.0", "_request"), response);
        assertEmpty("IIP-SSO01-ad-idp-01", response);
    }

    @Test
    void responseVersionIsComparedWithItsCorrelatedRequest() {
        var request = request("https://idp.example/sso", "2.0", "_request");
        assertOutcome("IIP-SSO01-en-idp-01", Outcome.SATISFIED, request,
                response("POST", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
        assertOutcome("IIP-SSO01-en-idp-01", Outcome.VIOLATED, request,
                response("POST", "https://suite.example/acs", "2.1", "_request", assertion("issuer", null, null)));
        assertEmpty("IIP-SSO01-en-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "", assertion("issuer", null, null)));
    }

    @Test
    void responseIssuerIsCheckedAgainstTheTargetEntityWhenPresent() {
        var expected = "https://idp.example/realms/samlier";
        assertEquals(
                Outcome.SATISFIED,
                NormalFlowBrowserObservation.evaluate(
                                "IIP-SSO01-h-idp-01",
                                List.of(responseWithIssuer(expected, null)),
                                expected)
                        .orElseThrow().outcome());
        assertEquals(
                Outcome.SATISFIED_WITH_NOTE,
                NormalFlowBrowserObservation.evaluate(
                                "IIP-SSO01-h-idp-01",
                                List.of(response("POST", "https://suite.example/acs", "2.0", "_request",
                                        assertion("issuer", null, null))),
                                expected)
                        .orElseThrow().outcome());
        assertEquals(
                Outcome.VIOLATED,
                NormalFlowBrowserObservation.evaluate(
                                "IIP-SSO01-h-idp-01",
                                List.of(responseWithIssuer(expected, "urn:example:wrong-format")),
                                expected)
                        .orElseThrow().outcome());
        assertEquals(
                Outcome.VIOLATED,
                NormalFlowBrowserObservation.evaluate(
                                "IIP-SSO01-h-idp-01",
                                List.of(responseWithIssuer("https://wrong.example/idp", null)),
                                expected)
                        .orElseThrow().outcome());
        assertTrue(NormalFlowBrowserObservation.evaluate(
                "IIP-SSO01-h-idp-01", List.of(responseWithIssuer(expected, null)), null).isEmpty());
    }

    @Test
    void bearerNotBeforeCheckDoesNotConfuseConditionsNotBefore() {
        assertOutcome("IIP-SSO01-k1-idp-01", Outcome.SATISFIED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", null, "2026-08-30T00:00:00Z")));
        assertOutcome("IIP-SSO01-k1-idp-01", Outcome.VIOLATED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", "2026-08-30T00:00:00Z", "2026-08-30T00:00:00Z")));
    }

    @Test
    void everyIssuedAssertionNeedsABearerConfirmation() {
        assertOutcome("IIP-SSO01-j-idp-01", Outcome.SATISFIED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", null, null)));
        assertOutcome("IIP-SSO01-j-idp-01", Outcome.VIOLATED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", null, null) + assertionWithoutBearer("issuer")));
    }

    @Test
    void everyBearerAssertionNeedsTheExactRequesterAudience() {
        var request = request("https://idp.example/sso", "2.0", "_request");
        assertEmpty("IIP-SSO01-m-idp-01", request,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithAudience("issuer", "https://suite.example/sp")));
        assertOutcome("IIP-SSO01-m-idp-01", Outcome.SATISFIED, request,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithAudience("issuer", "https://suite.example/sp")
                                + assertionWithAudience("issuer", "https://suite.example/sp")
                                .replace("_assertion", "_assertion-two")));
        assertOutcome("IIP-SSO01-m-idp-01", Outcome.VIOLATED, request,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithAudience("issuer", "https://suite.example/sp/")));
    }

    @Test
    void twoConsecutiveFlowsProveBothResponseAndBearerCorrelation() {
        var requestOne = request("https://idp.example/sso", "2.0", "_one");
        var requestTwo = request("https://idp.example/sso", "2.0", "_two");
        var responseOne = response("POST", "https://suite.example/acs", "2.0", "_one",
                assertionWithConfirmation("issuer", "_one"));
        var responseTwo = response("POST", "https://suite.example/acs", "2.0", "_two",
                assertionWithConfirmation("issuer", "_two"));
        assertEmpty("IIP-SSO01-k2-idp-01", requestOne, responseOne);
        assertOutcome("IIP-SSO01-k2-idp-01", Outcome.SATISFIED,
                requestOne, responseOne, requestTwo, responseTwo);
        assertOutcome("IIP-SSO01-ap-idp-01", Outcome.SATISFIED,
                requestOne, responseOne, requestTwo, responseTwo);
        assertOutcome("IIP-SSO01-k2-idp-01", Outcome.VIOLATED,
                requestOne, response("POST", "https://suite.example/acs", "2.0", "_one",
                        assertionWithConfirmation("issuer", "_stale")));
        assertOutcome("IIP-SSO01-ap-idp-01", Outcome.VIOLATED,
                requestOne, response("POST", "https://suite.example/acs", "2.0", "_unknown",
                        assertionWithConfirmation("issuer", "_unknown")));
    }

    @Test
    void aSingleAssertionDoesNotProveTheRequiredMultipleAssertionVariant() {
        assertEmpty("IIP-SSO01-i1-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)));
        assertOutcome("IIP-SSO01-i1-idp-01", Outcome.VIOLATED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer-a", null, null) + assertion("issuer-b", null, null)));
        assertOutcome("IIP-SSO01-i1-idp-01", Outcome.SATISFIED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", null, null) + assertion("issuer", null, null)));
    }

    @Test
    void nameIdLimitsCountUnicodeCodePointsAndRequireTheRelevantFormat() {
        var within = "x".repeat(255) + "\uD83D\uDE00";
        var over = within + "y";
        assertOutcome("IIP-SSO05-b1-idp-01", Outcome.SATISFIED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithNameId("issuer", NormalFlowBrowserObservation.TRANSIENT, within)));
        assertOutcome("IIP-SSO05-b1-idp-01", Outcome.VIOLATED,
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithNameId("issuer", NormalFlowBrowserObservation.TRANSIENT, over)));
        assertEmpty("IIP-SSO05-a2-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertionWithNameId("issuer", NormalFlowBrowserObservation.TRANSIENT, "value")));
    }

    @Test
    void correlatedPolicyRequestProvesPersistentAndTransientSupport() {
        var persistentRequest = requestWithNameId("_persistent", NormalFlowBrowserObservation.PERSISTENT);
        var persistentResponse = response(
                "POST", "https://suite.example/acs", "2.0", "_persistent",
                assertionWithNameId("issuer", NormalFlowBrowserObservation.PERSISTENT, "persistent-id"));
        assertOutcome("IIP-SSO05-a-idp-01", Outcome.SATISFIED,
                persistentRequest, persistentResponse);

        var transientRequest = requestWithNameId("_transient", NormalFlowBrowserObservation.TRANSIENT);
        assertOutcome("IIP-SSO05-b-idp-01", Outcome.SATISFIED, transientRequest,
                response("POST", "https://suite.example/acs", "2.0", "_transient",
                        assertionWithNameId("issuer", NormalFlowBrowserObservation.TRANSIENT, "transient-id")));
        assertOutcome("IIP-SSO05-b-idp-01", Outcome.VIOLATED, transientRequest,
                response("POST", "https://suite.example/acs", "2.0", "_transient",
                        assertionWithNameId("issuer", NormalFlowBrowserObservation.PERSISTENT, "wrong-id")));
        assertEmpty("IIP-SSO05-a-idp-01", persistentResponse);

        assertTrue(NormalFlowBrowserObservation.acceptsActiveScenarioEvidence("IIP-SSO05-a-idp-01"));
        assertTrue(!NormalFlowBrowserObservation.acceptsActiveScenarioEvidence("IIP-SSO01-h-idp-01"));
    }

    @Test
    void optionalEncryptionIsRecordedWithoutFabricatingAViolation() {
        var outcome = evaluate("IIP-IDP09-b-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        assertion("issuer", null, null))).orElseThrow();
        assertEquals(Outcome.SATISFIED_WITH_NOTE, outcome.outcome());
        assertEquals(0, outcome.details().get("encrypted_ids"));
        assertEquals(0, outcome.details().get("encrypted_attributes"));
        assertEmpty("IIP-IDP09-b-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request",
                        "<saml:EncryptedAssertion/>"));
    }

    @Test
    void malformedEvidenceNeverGetsSilentlyDroppedFromAnAutomaticPass() {
        assertEmpty("IIP-SSO01-x-idp-01",
                response("POST", "https://suite.example/acs", "2.0", "_request", assertion("issuer", null, null)),
                message("POST", "https://suite.example/acs", "<broken"));
    }

    private void assertOutcome(String caseId, Outcome expected, NormalFlowBrowserObservation.Message... messages) {
        assertEquals(expected, evaluate(caseId, messages).orElseThrow().outcome());
    }

    private void assertEmpty(String caseId, NormalFlowBrowserObservation.Message... messages) {
        assertTrue(evaluate(caseId, messages).isEmpty());
    }

    private java.util.Optional<org.samlier.core.evaluation.CaseOutcome> evaluate(
            String caseId, NormalFlowBrowserObservation.Message... messages) {
        return NormalFlowBrowserObservation.evaluate(caseId, List.of(messages));
    }

    private NormalFlowBrowserObservation.Message request(String url, String version, String id) {
        return message("GET", url, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="%s" Version="%s">
                  <saml:Issuer>https://suite.example/sp</saml:Issuer>
                </samlp:AuthnRequest>
                """.formatted(id, version));
    }

    private NormalFlowBrowserObservation.Message response(
            String method, String url, String version, String inResponseTo, String assertions) {
        return message(method, url, """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_response" Version="%s" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                  %s
                </samlp:Response>
                """.formatted(version, inResponseTo, assertions));
    }

    private NormalFlowBrowserObservation.Message requestWithNameId(String id, String format) {
        return message("POST", "https://idp.example/sso", """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="%s" Version="2.0">
                  <saml:Issuer>https://suite.example/sp</saml:Issuer>
                  <samlp:NameIDPolicy Format="%s"/>
                </samlp:AuthnRequest>
                """.formatted(id, format));
    }

    private NormalFlowBrowserObservation.Message responseWithIssuer(String issuer, String format) {
        var formatAttribute = format == null ? "" : " Format=\"" + format + "\"";
        return message("POST", "https://suite.example/acs", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_response" Version="2.0" InResponseTo="_request">
                  <saml:Issuer%s>%s</saml:Issuer>
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                  %s
                </samlp:Response>
                """.formatted(formatAttribute, issuer, assertion(issuer, null, null)));
    }

    private String assertion(String issuer, String subjectNotBefore, String conditionsNotBefore) {
        var subjectAttribute = subjectNotBefore == null ? "" : " NotBefore=\"" + subjectNotBefore + "\"";
        var conditions = conditionsNotBefore == null ? "" : "<saml:Conditions NotBefore=\"" + conditionsNotBefore + "\"/>";
        return """
                <saml:Assertion ID="_assertion" Version="2.0">
                  <saml:Issuer>%s</saml:Issuer>
                  <saml:Subject><saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
                    <saml:SubjectConfirmationData%s/>
                  </saml:SubjectConfirmation></saml:Subject>
                  <saml:AuthnStatement AuthnInstant="2026-08-30T00:00:00Z"/>
                  %s
                </saml:Assertion>
                """.formatted(issuer, subjectAttribute, conditions);
    }

    private String assertionWithNameId(String issuer, String format, String value) {
        return """
                <saml:Assertion ID="_assertion" Version="2.0">
                  <saml:Issuer>%s</saml:Issuer>
                  <saml:Subject><saml:NameID Format="%s">%s</saml:NameID></saml:Subject>
                </saml:Assertion>
                """.formatted(issuer, format, value);
    }

    private String assertionWithoutBearer(String issuer) {
        return """
                <saml:Assertion ID="_assertion-other" Version="2.0">
                  <saml:Issuer>%s</saml:Issuer>
                  <saml:Subject><saml:SubjectConfirmation Method="urn:example:holder-of-key"/></saml:Subject>
                  <saml:AuthnStatement AuthnInstant="2026-08-30T00:00:00Z"/>
                </saml:Assertion>
                """.formatted(issuer);
    }

    private String assertionWithAudience(String issuer, String audience) {
        return assertion(issuer, null, null).replace("</saml:Assertion>", """
                  <saml:Conditions><saml:AudienceRestriction><saml:Audience>%s</saml:Audience>
                  </saml:AudienceRestriction></saml:Conditions>
                </saml:Assertion>
                """.formatted(audience));
    }

    private String assertionWithConfirmation(String issuer, String inResponseTo) {
        return assertion(issuer, null, null).replace(
                "<saml:SubjectConfirmationData/>",
                "<saml:SubjectConfirmationData InResponseTo=\"" + inResponseTo + "\"/>");
    }

    private NormalFlowBrowserObservation.Message message(String method, String url, String xml) {
        return new NormalFlowBrowserObservation.Message(
                "entry-" + Math.abs(xml.hashCode()), method, url, xml.getBytes(StandardCharsets.UTF_8));
    }
}
