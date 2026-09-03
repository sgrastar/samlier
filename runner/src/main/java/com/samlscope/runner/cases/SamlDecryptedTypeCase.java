package com.samlscope.runner.cases;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.crypto.SamlXmlDecrypter;
import com.samlscope.saml.crypto.SamlElementDecrypter;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Checks the plaintext element permitted inside each SAML encrypted wrapper. */
public final class SamlDecryptedTypeCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private final SamlElementDecrypter decrypter;

    public SamlDecryptedTypeCase() {
        this(new SamlXmlDecrypter());
    }

    SamlDecryptedTypeCase(SamlElementDecrypter decrypter) {
        this.decrypter = java.util.Objects.requireNonNull(decrypter, "decrypter");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages, PrivateKey key) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) return CaseOutcome.notVerified(
                "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        var inspected = new ArrayList<EvidenceRef>();
        var failures = new ArrayList<EvidenceRef>();
        var violations = new ArrayList<String>();
        var observed = 0;
        for (var message : messages) {
            try {
                var document = SecureXml.parse(message.xml());
                for (var wrapperName : List.of("EncryptedAssertion", "EncryptedID", "EncryptedAttribute")) {
                    var wrappers = document.getElementsByTagNameNS(ASSERTION, wrapperName);
                    for (var index = 0; index < wrappers.getLength(); index++) {
                        observed++;
                        var ref = new EvidenceRef("transcript", message.evidenceRef() + "#" + wrapperName + "[" + index + "]");
                        inspected.add(ref);
                        if (key == null) {
                            failures.add(ref);
                            continue;
                        }
                        try {
                            var plaintext = decrypter.decrypt((Element) wrappers.item(index), key);
                            switch (classification(wrapperName, plaintext)) {
                                case ALLOWED -> { }
                                case VIOLATION -> violations.add(
                                        wrapperName + "->{" + plaintext.getNamespaceURI() + "}" + plaintext.getLocalName());
                                case UNKNOWN_DERIVATION -> failures.add(ref);
                            }
                        } catch (SamlException unavailable) {
                            failures.add(ref);
                        }
                    }
                }
            } catch (SamlException malformed) {
                failures.add(new EvidenceRef("transcript", message.evidenceRef()));
            }
        }
        if (!violations.isEmpty()) return new CaseOutcome(
                Outcome.VIOLATED, null, "saml.encrypted-content-type.violated",
                "case.saml.encrypted-content-type.violated", inspected,
                java.util.Map.of("observed_wrappers", observed, "violations", violations));
        if (!failures.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "encrypted_content_not_decryptable", "saml.encrypted-content-type.not-decrypted",
                "case.saml.encrypted-content-type.not-decrypted", failures,
                java.util.Map.of("observed_wrappers", observed, "undecrypted_wrappers", failures.size()));
        return new CaseOutcome(
                observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED,
                null, "saml.encrypted-content-type.satisfied", "case.saml.encrypted-content-type.satisfied",
                inspected, java.util.Map.of("observed_wrappers", observed));
    }

    private Classification classification(String wrapperName, Element plaintext) {
        if (!ASSERTION.equals(plaintext.getNamespaceURI())) return Classification.UNKNOWN_DERIVATION;
        var standardElementAllowed = switch (wrapperName) {
            case "EncryptedAssertion" -> "Assertion".equals(plaintext.getLocalName());
            case "EncryptedAttribute" -> "Attribute".equals(plaintext.getLocalName());
            case "EncryptedID" -> "NameID".equals(plaintext.getLocalName()) || "Assertion".equals(plaintext.getLocalName());
            default -> false;
        };
        if (!standardElementAllowed) return Classification.VIOLATION;
        var xsiType = plaintext.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type");
        if (xsiType.isBlank()) return Classification.ALLOWED;
        var separator = xsiType.indexOf(':');
        var prefix = separator < 0 ? null : xsiType.substring(0, separator);
        var localType = separator < 0 ? xsiType : xsiType.substring(separator + 1);
        var typeNamespace = plaintext.lookupNamespaceURI(prefix);
        if (!ASSERTION.equals(typeNamespace)) return Classification.UNKNOWN_DERIVATION;
        var typeAllowed = switch (wrapperName) {
            case "EncryptedAssertion" -> "AssertionType".equals(localType);
            case "EncryptedAttribute" -> "AttributeType".equals(localType);
            case "EncryptedID" -> List.of("BaseIDAbstractType", "NameIDType", "AssertionType").contains(localType);
            default -> false;
        };
        return typeAllowed ? Classification.ALLOWED : Classification.VIOLATION;
    }

    private enum Classification { ALLOWED, VIOLATION, UNKNOWN_DERIVATION }
}
