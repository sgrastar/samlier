package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.EmptyValue;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.NullValue;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.TextValue;

class SamlAttributeValueCaseTest {
    @Test
    void noValueIsDifferentFromAnEmptyValue() {
        var fixture = fixture("no-values", List.of());
        assertOutcome(SamlAttributeValueCase.Rule.NO_VALUES, fixture, Outcome.SATISFIED, attribute("no-values", ""));
        assertOutcome(SamlAttributeValueCase.Rule.NO_VALUES, fixture, Outcome.VIOLATED,
                attribute("no-values", "<saml:AttributeValue/>"));
    }

    @Test
    void emptyValueRequiresOneTrulyEmptyNonNilElement() {
        var fixture = fixture("empty", List.of(EmptyValue.INSTANCE));
        assertOutcome(SamlAttributeValueCase.Rule.EMPTY_VALUE, fixture, Outcome.SATISFIED,
                attribute("empty", "<saml:AttributeValue/>"));
        assertOutcome(SamlAttributeValueCase.Rule.EMPTY_VALUE, fixture, Outcome.VIOLATED, attribute("empty", ""));
        assertOutcome(SamlAttributeValueCase.Rule.EMPTY_VALUE, fixture, Outcome.VIOLATED,
                attribute("empty", "<saml:AttributeValue> </saml:AttributeValue>"));
        assertOutcome(SamlAttributeValueCase.Rule.EMPTY_VALUE, fixture, Outcome.VIOLATED,
                attribute("empty", "<saml:AttributeValue xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>"));
    }

    @Test
    void nullRequiresNilTrueAndNoChildren() {
        var fixture = fixture("nullable", List.of(NullValue.INSTANCE));
        assertOutcome(SamlAttributeValueCase.Rule.NULL_VALUE, fixture, Outcome.SATISFIED,
                attribute("nullable", "<saml:AttributeValue xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"1\"/>"));
        assertOutcome(SamlAttributeValueCase.Rule.NULL_VALUE, fixture, Outcome.VIOLATED,
                attribute("nullable", "<saml:AttributeValue/>"));
        assertOutcome(SamlAttributeValueCase.Rule.NULL_VALUE, fixture, Outcome.VIOLATED,
                attribute("nullable", "<saml:AttributeValue xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\">text</saml:AttributeValue>"));
    }

    @Test
    void eachDiscreteValueRequiresItsOwnElement() {
        var fixture = fixture("affiliation", List.of(new TextValue("faculty"), new TextValue("member")));
        assertOutcome(SamlAttributeValueCase.Rule.DISCRETE_VALUES, fixture, Outcome.SATISFIED,
                attribute("affiliation", "<saml:AttributeValue>member</saml:AttributeValue><saml:AttributeValue>faculty</saml:AttributeValue>"));
        assertOutcome(SamlAttributeValueCase.Rule.DISCRETE_VALUES, fixture, Outcome.VIOLATED,
                attribute("affiliation", "<saml:AttributeValue>faculty;member</saml:AttributeValue>"));
        assertOutcome(SamlAttributeValueCase.Rule.DISCRETE_VALUES, fixture, Outcome.VIOLATED,
                attribute("affiliation", "<saml:AttributeValue>faculty</saml:AttributeValue>"));
    }

    @Test
    void missingConfiguredAttributeIsNotInventedAsATargetViolation() {
        var fixture = fixture("nullable", List.of(NullValue.INSTANCE));
        assertOutcome(SamlAttributeValueCase.Rule.NULL_VALUE, fixture, Outcome.SATISFIED_WITH_NOTE,
                attribute("other", "<saml:AttributeValue>value</saml:AttributeValue>"));
    }

    @Test
    void fixtureKindMustMatchTheApprovedCaseRule() {
        assertThrows(IllegalArgumentException.class, () -> new SamlAttributeValueCase(
                SamlAttributeValueCase.Rule.NULL_VALUE, fixture("wrong", List.of(EmptyValue.INSTANCE))));
    }

    private SamlAttributeReleaseFixture fixture(String name, List<SamlAttributeReleaseFixture.Value> values) {
        return new SamlAttributeReleaseFixture(name, null, values);
    }

    private String attribute(String name, String values) {
        return "<saml:Attribute xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" Name=\""
                + name + "\">" + values + "</saml:Attribute>";
    }

    private void assertOutcome(
            SamlAttributeValueCase.Rule rule,
            SamlAttributeReleaseFixture fixture,
            Outcome expected,
            String xml) {
        var outcome = new SamlAttributeValueCase(rule, fixture).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
