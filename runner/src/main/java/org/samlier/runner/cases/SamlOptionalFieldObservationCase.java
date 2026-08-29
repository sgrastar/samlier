package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Informational observation of one explicitly selected optional SAML element or attribute. */
public final class SamlOptionalFieldObservationCase {
    public record Selector(QName element, QName attribute) {
        public Selector {
            java.util.Objects.requireNonNull(element, "element");
        }

        public static Selector element(QName element) { return new Selector(element, null); }
        public static Selector attribute(QName element, QName attribute) { return new Selector(element, attribute); }
    }

    private final Selector selector;

    public SamlOptionalFieldObservationCase(Selector selector) {
        this.selector = java.util.Objects.requireNonNull(selector, "selector");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified("no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        }
        var evidence = new ArrayList<EvidenceRef>();
        var unparseable = new ArrayList<EvidenceRef>();
        var containingElements = 0;
        var observedFields = 0;
        for (var message : messages) {
            var reference = new EvidenceRef("transcript", message.evidenceRef());
            try {
                var document = SecureXml.parse(message.xml());
                var elements = document.getElementsByTagNameNS(
                        selector.element().getNamespaceURI(), selector.element().getLocalPart());
                containingElements += elements.getLength();
                for (var index = 0; index < elements.getLength(); index++) {
                    if (selector.attribute() == null || hasAttribute((Element) elements.item(index), selector.attribute())) {
                        observedFields++;
                        evidence.add(reference);
                    }
                }
            } catch (SamlException malformed) {
                unparseable.add(reference);
            }
        }
        if (!unparseable.isEmpty()) {
            return new CaseOutcome(Outcome.NOT_VERIFIED, "target_message_unparseable",
                    "saml.optional-field.unparseable", "case.saml.optional-field.unparseable", unparseable,
                    java.util.Map.of("unparseable_messages", unparseable.size()));
        }
        return new CaseOutcome(Outcome.SATISFIED, null, "saml.optional-field.recorded",
                "case.saml.optional-field.recorded", evidence.stream().distinct().toList(),
                java.util.Map.of(
                        "selector", displaySelector(),
                        "containing_elements", containingElements,
                        "observed_fields", observedFields,
                        "present", observedFields > 0));
    }

    private boolean hasAttribute(Element element, QName attribute) {
        return attribute.getNamespaceURI().isEmpty()
                ? element.hasAttribute(attribute.getLocalPart())
                : element.hasAttributeNS(attribute.getNamespaceURI(), attribute.getLocalPart());
    }

    private String displaySelector() {
        var element = "{" + selector.element().getNamespaceURI() + "}" + selector.element().getLocalPart();
        if (selector.attribute() == null) return element;
        return element + "/@{" + selector.attribute().getNamespaceURI() + "}" + selector.attribute().getLocalPart();
    }
}
