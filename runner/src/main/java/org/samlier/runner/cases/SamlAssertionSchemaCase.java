package org.samlier.runner.cases;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.crypto.SamlElementDecrypter;
import org.samlier.saml.crypto.SamlXmlDecrypter;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SamlSchemaValidation;
import org.samlier.saml.normal.SamlSchemaValidation.SchemaKind;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Validates plaintext assertions, including assertions recovered from EncryptedAssertion. */
public final class SamlAssertionSchemaCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private final SamlElementDecrypter decrypter;

    public SamlAssertionSchemaCase() {
        this(new SamlXmlDecrypter());
    }

    SamlAssertionSchemaCase(SamlElementDecrypter decrypter) {
        this.decrypter = java.util.Objects.requireNonNull(decrypter, "decrypter");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages, PrivateKey key) {
        messages = List.copyOf(messages == null ? List.of() : messages);
        if (messages.isEmpty()) return CaseOutcome.notVerified(
                "no_target_generated_saml_messages", "transcript.no-target-generated-saml");
        var inspected = new ArrayList<EvidenceRef>();
        var violations = new ArrayList<String>();
        var unavailable = new ArrayList<EvidenceRef>();
        var observed = 0;
        for (var message : messages) {
            try {
                var document = SecureXml.parse(message.xml());
                var assertions = document.getElementsByTagNameNS(ASSERTION, "Assertion");
                for (var index = 0; index < assertions.getLength(); index++) {
                    observed++;
                    var ref = new EvidenceRef("transcript", message.evidenceRef() + "#Assertion[" + index + "]");
                    inspected.add(ref);
                    inspectAssertion((Element) assertions.item(index), ref, violations);
                }
                var encrypted = document.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion");
                for (var index = 0; index < encrypted.getLength(); index++) {
                    observed++;
                    var ref = new EvidenceRef("transcript", message.evidenceRef() + "#EncryptedAssertion[" + index + "]");
                    inspected.add(ref);
                    if (key == null) {
                        unavailable.add(ref);
                        continue;
                    }
                    try {
                        var plaintext = decrypter.decrypt((Element) encrypted.item(index), key);
                        if (!ASSERTION.equals(plaintext.getNamespaceURI()) || !"Assertion".equals(plaintext.getLocalName())) {
                            violations.add(ref.reference() + ":wrong-plaintext-type");
                        } else {
                            inspectAssertion(plaintext, ref, violations);
                        }
                    } catch (SamlException failure) {
                        unavailable.add(ref);
                    }
                }
            } catch (SamlException malformed) {
                unavailable.add(new EvidenceRef("transcript", message.evidenceRef()));
            }
        }
        if (!violations.isEmpty()) return new CaseOutcome(
                Outcome.VIOLATED, null, "saml.assertion-schema.violated", "case.saml.assertion-schema.violated",
                inspected, java.util.Map.of("observed_assertions", observed, "violations", violations));
        if (!unavailable.isEmpty()) return new CaseOutcome(
                Outcome.NOT_VERIFIED, "assertion_not_inspectable", "saml.assertion-schema.not-inspectable",
                "case.saml.assertion-schema.not-inspectable", unavailable,
                java.util.Map.of("observed_assertions", observed, "uninspectable_assertions", unavailable.size()));
        return new CaseOutcome(
                observed == 0 ? Outcome.SATISFIED_WITH_NOTE : Outcome.SATISFIED,
                null, "saml.assertion-schema.satisfied", "case.saml.assertion-schema.satisfied",
                inspected, java.util.Map.of("observed_assertions", observed));
    }

    private void inspectAssertion(Element assertion, EvidenceRef evidence, List<String> violations) {
        if (!SamlSchemaValidation.isValid(assertion, SchemaKind.ASSERTION)
                || !"2.0".equals(assertion.getAttribute("Version"))
                || !assertion.getAttribute("IssueInstant").endsWith("Z")) {
            violations.add(evidence.reference());
        }
    }
}
