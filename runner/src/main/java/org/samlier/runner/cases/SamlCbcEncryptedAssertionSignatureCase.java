package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.saml.crypto.XmlSignatureVerifier;
import org.w3c.dom.Element;

/** Requires a valid Response signature when its EncryptedAssertion uses CBC-mode content encryption. */
public final class SamlCbcEncryptedAssertionSignatureCase {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String XMLENC = "http://www.w3.org/2001/04/xmlenc#";
    private final List<X509Certificate> targetSigningCertificates;
    private final XmlSignatureVerifier verifier;

    public SamlCbcEncryptedAssertionSignatureCase(X509Certificate targetSigningCertificate) {
        this(List.of(targetSigningCertificate), new XmlSignatureVerifier());
    }

    SamlCbcEncryptedAssertionSignatureCase(
            List<X509Certificate> targetSigningCertificates, XmlSignatureVerifier verifier) {
        this.targetSigningCertificates = List.copyOf(targetSigningCertificates);
        if (this.targetSigningCertificates.isEmpty()) throw new IllegalArgumentException("targetSigningCertificates must not be empty");
        this.verifier = java.util.Objects.requireNonNull(verifier, "verifier");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.cbc-encrypted-assertion.response-signature", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var responses = document.getElementsByTagNameNS(PROTOCOL, "Response");
        for (var responseIndex = 0; responseIndex < responses.getLength(); responseIndex++) {
            var response = (Element) responses.item(responseIndex);
            if (!containsCbcEncryptedAssertion(response)) continue;
            observed++;
            if (targetSigningCertificates.stream().noneMatch(
                    certificate -> verifier.hasValidEnvelopedSignature(response, certificate))) {
                violations.add("{" + PROTOCOL + "}Response/ds:Signature");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean containsCbcEncryptedAssertion(Element response) {
        var encryptedAssertions = response.getElementsByTagNameNS(ASSERTION, "EncryptedAssertion");
        for (var index = 0; index < encryptedAssertions.getLength(); index++) {
            var encryptedData = directChild((Element) encryptedAssertions.item(index), XMLENC, "EncryptedData");
            var method = encryptedData == null ? null : directChild(encryptedData, XMLENC, "EncryptionMethod");
            if (method != null && method.getAttribute("Algorithm").endsWith("-cbc")) return true;
        }
        return false;
    }

    private Element directChild(Element parent, String namespace, String localName) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }
}
