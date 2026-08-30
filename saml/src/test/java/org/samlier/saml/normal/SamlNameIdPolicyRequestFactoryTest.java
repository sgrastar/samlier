package org.samlier.saml.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.samlier.saml.normal.SamlNameIdPolicyRequestFactory.Policy;
import org.w3c.dom.Element;

class SamlNameIdPolicyRequestFactoryTest {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private final SamlNameIdPolicyRequestFactory factory = new SamlNameIdPolicyRequestFactory();

    @Test
    void preservesOmittedPolicyAndIndependentlyOmittedAttributes() {
        var omitted = request(Policy.omitted());
        assertEquals(0, omitted.getElementsByTagNameNS(PROTOCOL, "NameIDPolicy").getLength());

        var noAllowCreate = policy(request(new Policy(true,
                SamlNameIdPolicyRequestFactory.TRANSIENT, null, null)));
        assertEquals(SamlNameIdPolicyRequestFactory.TRANSIENT, noAllowCreate.getAttribute("Format"));
        assertFalse(noAllowCreate.hasAttribute("AllowCreate"));

        var noFormat = policy(request(new Policy(true, null, null, true)));
        assertEquals("true", noFormat.getAttribute("AllowCreate"));
        assertFalse(noFormat.hasAttribute("Format"));
    }

    @Test
    void buildsSchemaValidPolicyValuesAndUniqueUnknownUris() {
        var document = request(new Policy(true,
                factory.unknownFormat("_request-one"), factory.unknownSpNameQualifier("_request-one"), true));
        assertTrue(SamlSchemaValidation.isValid(
                document.getDocumentElement(), SamlSchemaValidation.SchemaKind.PROTOCOL));
        var policy = policy(document);
        assertTrue(policy.getAttribute("Format").endsWith("request-one"));
        assertTrue(policy.getAttribute("SPNameQualifier").endsWith("request-one"));
        assertEquals("true", policy.getAttribute("AllowCreate"));
    }

    private org.w3c.dom.Document request(Policy policy) {
        return SecureXml.parse(factory.build(
                "_request", URI.create("https://idp.example/sso"), "https://suite.example/sp",
                URI.create("https://suite.example/acs"), Instant.parse("2026-08-30T00:00:00Z"), policy));
    }

    private Element policy(org.w3c.dom.Document document) {
        return (Element) document.getElementsByTagNameNS(PROTOCOL, "NameIDPolicy").item(0);
    }
}
