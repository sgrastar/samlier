package org.samlier.runner.cases;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;

/** Detects ciphertext reuse across distinct EncryptedID encryption operations. */
public final class SamlEncryptedIdentifierUniquenessCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String XMLENC = "http://www.w3.org/2001/04/xmlenc#";

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) {
            return CaseOutcome.notVerified("no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        }
        var distinctDocuments = new HashSet<String>();
        var firstCipherEvidence = new HashMap<String, EvidenceRef>();
        var evidence = new ArrayList<EvidenceRef>();
        var violations = new ArrayList<String>();
        var unparseable = new ArrayList<EvidenceRef>();
        var observed = 0;
        for (var message : messages) {
            if (!distinctDocuments.add(digest(message.xml()))) continue;
            var messageEvidence = new EvidenceRef("transcript", message.evidenceRef());
            try {
                var document = SecureXml.parse(message.xml());
                var encryptedIds = document.getElementsByTagNameNS(ASSERTION, "EncryptedID");
                for (var index = 0; index < encryptedIds.getLength(); index++) {
                    var encryptedData = directChild((org.w3c.dom.Element) encryptedIds.item(index), XMLENC, "EncryptedData");
                    var cipherData = encryptedData == null ? null : directChild(encryptedData, XMLENC, "CipherData");
                    var cipherValue = cipherData == null ? null : directChild(cipherData, XMLENC, "CipherValue");
                    if (cipherValue == null) continue;
                    var cipher = cipherValue.getTextContent().replaceAll("\\s+", "");
                    if (cipher.isEmpty()) continue;
                    observed++;
                    evidence.add(messageEvidence);
                    var previous = firstCipherEvidence.putIfAbsent(cipher, messageEvidence);
                    if (previous != null) {
                        violations.add(previous.reference() + " -> " + message.evidenceRef());
                    }
                }
            } catch (SamlException malformed) {
                unparseable.add(messageEvidence);
            }
        }
        if (!violations.isEmpty()) {
            return new CaseOutcome(Outcome.VIOLATED, null, "saml.encrypted-id.ciphertext-reused",
                    "case.saml.encrypted-id.ciphertext-reused", evidence.stream().distinct().toList(),
                    java.util.Map.of("observed_ciphertexts", observed, "reused_ciphertexts", violations));
        }
        if (!unparseable.isEmpty()) {
            return new CaseOutcome(Outcome.NOT_VERIFIED, "target_message_unparseable",
                    "saml.encrypted-id.unparseable", "case.saml.encrypted-id.unparseable", unparseable,
                    java.util.Map.of("observed_ciphertexts", observed, "unparseable_messages", unparseable.size()));
        }
        return new CaseOutcome(observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED, null,
                observed == 0 ? "saml.encrypted-id.not-observed" : "saml.encrypted-id.unique",
                observed == 0 ? "case.saml.encrypted-id.not-observed" : "case.saml.encrypted-id.unique",
                evidence.stream().distinct().toList(), java.util.Map.of("observed_ciphertexts", observed));
    }

    private String digest(byte[] value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private org.w3c.dom.Element directChild(org.w3c.dom.Element parent, String namespace, String localName) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof org.w3c.dom.Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }
}
