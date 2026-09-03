package com.samlscope.runner.cases;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive check that wrapped keys identify the Test Peer recipient. */
public final class SamlEncryptedKeyRecipientCase {
    private static final String XMLENC = "http://www.w3.org/2001/04/xmlenc#";
    private final String expectedRecipient;

    public SamlEncryptedKeyRecipientCase(String expectedRecipient) {
        if (expectedRecipient == null || expectedRecipient.isBlank()) {
            throw new IllegalArgumentException("expectedRecipient must not be blank");
        }
        URI.create(expectedRecipient);
        this.expectedRecipient = expectedRecipient;
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.encrypted-key.recipient", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var keys = document.getElementsByTagNameNS(XMLENC, "EncryptedKey");
        for (var index = 0; index < keys.getLength(); index++) {
            observed++;
            var key = (Element) keys.item(index);
            if (!key.hasAttribute("Recipient") || !expectedRecipient.equals(key.getAttribute("Recipient"))) {
                violations.add("{" + XMLENC + "}EncryptedKey/@Recipient");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }
}
