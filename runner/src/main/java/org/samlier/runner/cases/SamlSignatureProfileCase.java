package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Passive checks for the XML Signature profile incorporated by IIP-SSO01. */
public final class SamlSignatureProfileCase {
    public enum Rule {
        ENVELOPED,
        SIGNED_ROOT_ID,
        SINGLE_ROOT_REFERENCE,
        EXCLUSIVE_CANONICALIZATION,
        ALLOWED_TRANSFORMS,
        NO_OBJECT
    }

    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ENVELOPED = DS + "enveloped-signature";
    private static final String EXCLUSIVE = "http://www.w3.org/2001/10/xml-exc-c14n#";
    private static final String EXCLUSIVE_COMMENTS = EXCLUSIVE + "WithComments";
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(ENVELOPED, EXCLUSIVE, EXCLUSIVE_COMMENTS);

    private final Rule rule;

    public SamlSignatureProfileCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified(
                    "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        }
        var signatures = 0;
        var violations = new ArrayList<SignatureViolation>();
        var inspected = new ArrayList<EvidenceRef>();
        var unparseable = new ArrayList<EvidenceRef>();
        for (var message : messages) {
            var evidence = new EvidenceRef("transcript", message.evidenceRef());
            try {
                var document = SecureXml.parse(message.xml());
                var nodes = document.getElementsByTagNameNS(DS, "Signature");
                signatures += nodes.getLength();
                for (var index = 0; index < nodes.getLength(); index++) {
                    var signature = (Element) nodes.item(index);
                    var reason = violation(signature);
                    if (reason != null) violations.add(new SignatureViolation(evidence, reason));
                }
                inspected.add(evidence);
            } catch (SamlException malformed) {
                unparseable.add(evidence);
            }
        }
        var code = "saml.signature." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (!violations.isEmpty()) {
            return new CaseOutcome(
                    Outcome.VIOLATED, null, code + ".violated", "case." + code + ".violated",
                    violations.stream().map(SignatureViolation::evidence).distinct().toList(),
                    Map.of(
                            "inspected_signatures", signatures,
                            "violations", violations.stream().map(SignatureViolation::reason).toList(),
                            "unparseable_messages", unparseable.size()));
        }
        if (!unparseable.isEmpty()) {
            return new CaseOutcome(
                    Outcome.NOT_VERIFIED, "target_message_unparseable",
                    code + ".message-unparseable", "case." + code + ".message-unparseable",
                    unparseable, Map.of("inspected_signatures", signatures));
        }
        var outcome = signatures == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED;
        return new CaseOutcome(
                outcome, null,
                signatures == 0 ? code + ".no-signatures-observed" : code + ".satisfied",
                signatures == 0 ? "case." + code + ".no-signatures-observed" : "case." + code + ".satisfied",
                inspected, Map.of("inspected_signatures", signatures));
    }

    private String violation(Element signature) {
        return switch (rule) {
            case ENVELOPED -> envelopedViolation(signature);
            case SIGNED_ROOT_ID -> signedRoot(signature) == null || signedRoot(signature).getAttribute("ID").isBlank()
                    ? "signed-root-id-missing" : null;
            case SINGLE_ROOT_REFERENCE -> referenceViolation(signature);
            case EXCLUSIVE_CANONICALIZATION -> canonicalizationViolation(signature);
            case ALLOWED_TRANSFORMS -> transformViolation(signature);
            case NO_OBJECT -> signature.getElementsByTagNameNS(DS, "Object").getLength() > 0
                    ? "ds-object-present" : null;
        };
    }

    private String envelopedViolation(Element signature) {
        if (signedRoot(signature) == null) return "signature-not-child-of-saml-root";
        return algorithms(signature, "Transform").contains(ENVELOPED)
                ? null : "enveloped-signature-transform-missing";
    }

    private String referenceViolation(Element signature) {
        var root = signedRoot(signature);
        if (root == null || root.getAttribute("ID").isBlank()) return "signed-root-id-missing";
        var references = signature.getElementsByTagNameNS(DS, "Reference");
        if (references.getLength() != 1) return "reference-count-" + references.getLength();
        var uri = ((Element) references.item(0)).getAttribute("URI");
        return ("#" + root.getAttribute("ID")).equals(uri) ? null : "reference-does-not-match-signed-root";
    }

    private String canonicalizationViolation(Element signature) {
        var methods = algorithms(signature, "CanonicalizationMethod");
        if (methods.size() != 1 || !exclusive(methods.getFirst())) {
            return "canonicalization-method-not-exclusive";
        }
        var references = signature.getElementsByTagNameNS(DS, "Reference");
        if (references.getLength() == 0) return "exclusive-canonicalization-transform-missing";
        for (var index = 0; index < references.getLength(); index++) {
            if (algorithms((Element) references.item(index), "Transform").stream().noneMatch(this::exclusive)) {
                return "exclusive-canonicalization-transform-missing";
            }
        }
        return null;
    }

    private String transformViolation(Element signature) {
        return algorithms(signature, "Transform").stream().allMatch(ALLOWED_TRANSFORMS::contains)
                ? null : "unsupported-transform-present";
    }

    private Element signedRoot(Element signature) {
        var parent = signature.getParentNode();
        if (!(parent instanceof Element element)) return null;
        var namespace = element.getNamespaceURI();
        return ASSERTION.equals(namespace) || PROTOCOL.equals(namespace) ? element : null;
    }

    private List<String> algorithms(Element signature, String localName) {
        var result = new ArrayList<String>();
        var elements = signature.getElementsByTagNameNS(DS, localName);
        for (var index = 0; index < elements.getLength(); index++) {
            result.add(((Element) elements.item(index)).getAttribute("Algorithm"));
        }
        return result;
    }

    private boolean exclusive(String algorithm) {
        return EXCLUSIVE.equals(algorithm) || EXCLUSIVE_COMMENTS.equals(algorithm);
    }

    private record SignatureViolation(EvidenceRef evidence, String reason) {}
}
