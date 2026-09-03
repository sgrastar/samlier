package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** IIP-SSO01.af/ao: target-assigned SAML object IDs do not collide across distinct objects. */
public final class SamlIdentifierUniquenessCase {
    public enum Subject { SP_AUTHN_REQUEST, IDP_RESPONSE_AND_ASSERTION }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";

    private final Subject subject;

    public SamlIdentifierUniquenessCase(Subject subject) {
        this.subject = java.util.Objects.requireNonNull(subject, "subject");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified(
                    "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        }
        var firstById = new LinkedHashMap<String, Assignment>();
        var violations = new ArrayList<Collision>();
        var evidence = new ArrayList<EvidenceRef>();
        var unparseable = new ArrayList<EvidenceRef>();
        var observed = 0;
        for (var messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            var message = messages.get(messageIndex);
            var messageEvidence = new EvidenceRef("transcript", message.evidenceRef());
            try {
                var document = SecureXml.parse(message.xml());
                var selected = selectedElements(document);
                for (var elementIndex = 0; elementIndex < selected.size(); elementIndex++) {
                    var element = selected.get(elementIndex);
                    if (!element.hasAttribute("ID")) continue;
                    observed++;
                    var id = element.getAttribute("ID");
                    var current = new Assignment(messageIndex, elementIndex, element, messageEvidence);
                    var first = firstById.putIfAbsent(id, current);
                    if (first == null) continue;
                    var sameRetransmittedObject = first.messageIndex() != messageIndex
                            && first.element().isEqualNode(element);
                    if (!sameRetransmittedObject) {
                        violations.add(new Collision(id, first.evidence(), messageEvidence));
                    }
                }
                evidence.add(messageEvidence);
            } catch (SamlException malformed) {
                unparseable.add(messageEvidence);
            }
        }
        var code = subject == Subject.SP_AUTHN_REQUEST ? "saml.id.authn-request" : "saml.id.response-assertion";
        if (!violations.isEmpty()) {
            return new CaseOutcome(
                    Outcome.VIOLATED, null, code + ".collision", "case." + code + ".collision",
                    violations.stream()
                            .flatMap(value -> java.util.stream.Stream.of(value.first(), value.second()))
                            .distinct().toList(),
                    Map.of(
                            "observed_assignments", observed,
                            "colliding_ids", violations.stream().map(Collision::id).distinct().toList(),
                            "unparseable_messages", unparseable.size()));
        }
        if (!unparseable.isEmpty()) {
            return new CaseOutcome(
                    Outcome.NOT_VERIFIED, "target_message_unparseable",
                    code + ".message-unparseable", "case." + code + ".message-unparseable",
                    unparseable, Map.of("observed_assignments", observed));
        }
        var outcome = observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED;
        return new CaseOutcome(
                outcome, null,
                observed == 0 ? code + ".no-relevant-ids" : code + ".unique",
                observed == 0 ? "case." + code + ".no-relevant-ids" : "case." + code + ".unique",
                evidence, Map.of("observed_assignments", observed));
    }

    private List<Element> selectedElements(org.w3c.dom.Document document) {
        var selected = new ArrayList<Element>();
        if (subject == Subject.SP_AUTHN_REQUEST) {
            add(document, PROTOCOL, "AuthnRequest", selected);
        } else {
            add(document, PROTOCOL, "Response", selected);
            add(document, ASSERTION, "Assertion", selected);
        }
        return selected;
    }

    private void add(org.w3c.dom.Document document, String namespace, String localName, List<Element> selected) {
        var elements = document.getElementsByTagNameNS(namespace, localName);
        for (var index = 0; index < elements.getLength(); index++) selected.add((Element) elements.item(index));
    }

    private record Assignment(
            int messageIndex, int elementIndex, Element element, EvidenceRef evidence) {}
    private record Collision(String id, EvidenceRef first, EvidenceRef second) {}
}
