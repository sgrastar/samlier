package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.EmptyValue;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.NullValue;
import com.samlscope.runner.cases.SamlAttributeReleaseFixture.TextValue;
import org.w3c.dom.Element;

/** Checks AttributeValue serialization against a known source-side release fixture. */
public final class SamlAttributeValueCase {
    public enum Rule { NO_VALUES, EMPTY_VALUE, NULL_VALUE, DISCRETE_VALUES }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private final Rule rule;
    private final SamlAttributeReleaseFixture fixture;

    public SamlAttributeValueCase(Rule rule, SamlAttributeReleaseFixture fixture) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
        this.fixture = java.util.Objects.requireNonNull(fixture, "fixture");
        validateFixture();
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.attribute-value." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var attributes = document.getElementsByTagNameNS(ASSERTION, "Attribute");
        for (var index = 0; index < attributes.getLength(); index++) {
            var attribute = (Element) attributes.item(index);
            if (!matchesFixture(attribute)) continue;
            observed++;
            var values = directValues(attribute);
            var valid = switch (rule) {
                case NO_VALUES -> values.isEmpty();
                case EMPTY_VALUE -> values.size() == 1 && isEmpty(values.getFirst()) && !isNil(values.getFirst());
                case NULL_VALUE -> values.size() == 1 && isEmpty(values.getFirst()) && isNil(values.getFirst());
                case DISCRETE_VALUES -> discreteValuesMatch(values);
            };
            if (!valid) violations.add("{" + ASSERTION + "}Attribute[@Name='" + fixture.name() + "']");
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean matchesFixture(Element attribute) {
        if (!fixture.name().equals(attribute.getAttribute("Name"))) return false;
        return fixture.nameFormat() == null || fixture.nameFormat().equals(attribute.getAttribute("NameFormat"));
    }

    private List<Element> directValues(Element attribute) {
        var values = new ArrayList<Element>();
        for (var child = attribute.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && ASSERTION.equals(element.getNamespaceURI())
                    && "AttributeValue".equals(element.getLocalName())) values.add(element);
        }
        return values;
    }

    private boolean isEmpty(Element value) {
        return !value.hasChildNodes();
    }

    private boolean isNil(Element value) {
        var lexical = value.getAttributeNS(XSI, "nil");
        return "true".equals(lexical) || "1".equals(lexical);
    }

    private boolean discreteValuesMatch(List<Element> actual) {
        if (actual.size() != fixture.values().size()) return false;
        var expectedCounts = new HashMap<String, Integer>();
        for (var value : fixture.values()) {
            expectedCounts.merge(((TextValue) value).value(), 1, Integer::sum);
        }
        var actualCounts = new HashMap<String, Integer>();
        for (var value : actual) {
            actualCounts.merge(value.getTextContent(), 1, Integer::sum);
        }
        return expectedCounts.equals(actualCounts);
    }

    private void validateFixture() {
        var valid = switch (rule) {
            case NO_VALUES -> fixture.values().isEmpty();
            case EMPTY_VALUE -> fixture.values().equals(List.of(EmptyValue.INSTANCE));
            case NULL_VALUE -> fixture.values().equals(List.of(NullValue.INSTANCE));
            case DISCRETE_VALUES -> !fixture.values().isEmpty()
                    && fixture.values().stream().allMatch(TextValue.class::isInstance);
        };
        if (!valid) throw new IllegalArgumentException("Fixture values do not match rule " + rule);
    }
}
