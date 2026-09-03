package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlCoreStructureCaseTest {
    @Test
    void acceptsOnlyTopLevelStatusCodesAtTheTopLevel() {
        assertOutcome(SamlCoreStructureCase.Rule.TOP_LEVEL_STATUS_CODE, Outcome.SATISFIED, """
                <samlp:Status xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol">
                  <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Responder">
                    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:AuthnFailed"/>
                  </samlp:StatusCode>
                </samlp:Status>
                """);
        assertOutcome(SamlCoreStructureCase.Rule.TOP_LEVEL_STATUS_CODE, Outcome.VIOLATED, """
                <samlp:Status xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol">
                  <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:AuthnFailed"/>
                </samlp:Status>
                """);
    }

    @Test
    void genericStatementAndConditionRequireXsiType() {
        var typed = """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <saml:Statement xsi:type="saml:AuthnStatementType"/>
                  <saml:Conditions><saml:Condition xsi:type="saml:AudienceRestrictionType"/></saml:Conditions>
                </saml:Assertion>
                """;
        var untyped = typed.replace(" xsi:type=\"saml:AuthnStatementType\"", "")
                .replace(" xsi:type=\"saml:AudienceRestrictionType\"", "");
        assertOutcome(SamlCoreStructureCase.Rule.GENERIC_STATEMENT_TYPE, Outcome.SATISFIED, typed);
        assertOutcome(SamlCoreStructureCase.Rule.GENERIC_CONDITION_TYPE, Outcome.SATISFIED, typed);
        assertOutcome(SamlCoreStructureCase.Rule.GENERIC_STATEMENT_TYPE, Outcome.VIOLATED, untyped);
        assertOutcome(SamlCoreStructureCase.Rule.GENERIC_CONDITION_TYPE, Outcome.VIOLATED, untyped);
    }

    @Test
    void conditionsLimitOneTimeUseAndProxyRestrictionIndependently() {
        var duplicate = """
                <saml:Conditions xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:OneTimeUse/><saml:OneTimeUse/>
                  <saml:ProxyRestriction/><saml:ProxyRestriction/>
                </saml:Conditions>
                """;
        assertOutcome(SamlCoreStructureCase.Rule.ONE_TIME_USE_LIMIT, Outcome.VIOLATED, duplicate);
        assertOutcome(SamlCoreStructureCase.Rule.PROXY_RESTRICTION_LIMIT, Outcome.VIOLATED, duplicate);
    }

    @Test
    void subjectRulesUseOnlyTheirOwnStatementTrigger() {
        var noStatements = assertion("");
        var authn = assertion("<saml:AuthnStatement/>");
        var attribute = assertion("<saml:AttributeStatement/>");
        var subjectAndAuthn = assertion("<saml:Subject/><saml:AuthnStatement/>");

        assertOutcome(SamlCoreStructureCase.Rule.SUBJECT_WITHOUT_STATEMENTS, Outcome.VIOLATED, noStatements);
        assertOutcome(SamlCoreStructureCase.Rule.SUBJECT_WITHOUT_STATEMENTS, Outcome.SATISFIED_WITH_NOTE, authn);
        assertOutcome(SamlCoreStructureCase.Rule.SUBJECT_FOR_AUTHN_STATEMENT, Outcome.VIOLATED, authn);
        assertOutcome(SamlCoreStructureCase.Rule.SUBJECT_FOR_AUTHN_STATEMENT, Outcome.SATISFIED, subjectAndAuthn);
        assertOutcome(SamlCoreStructureCase.Rule.SUBJECT_FOR_ATTRIBUTE_STATEMENT, Outcome.VIOLATED, attribute);
    }

    private String assertion(String children) {
        return """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">%s</saml:Assertion>
                """.formatted(children);
    }

    private void assertOutcome(SamlCoreStructureCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlCoreStructureCase(rule).evaluate(List.of(message(xml)));
        assertEquals(expected, outcome.outcome(), rule.name());
    }

    private TargetTranscriptMessages.Message message(String xml) {
        return new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8));
    }
}
