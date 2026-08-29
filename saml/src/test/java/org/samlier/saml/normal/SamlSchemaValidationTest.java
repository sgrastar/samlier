package org.samlier.saml.normal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.samlier.saml.normal.SamlSchemaValidation.SchemaKind;

class SamlSchemaValidationTest {
    @Test
    void validatesProtocolDocumentsOfflineWithImportedSchemas() {
        assertTrue(valid(SchemaKind.PROTOCOL, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_request" Version="2.0" IssueInstant="2026-08-29T00:00:00Z"/>
                """));
        assertFalse(valid(SchemaKind.PROTOCOL, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  Version="2.0" IssueInstant="2026-08-29T00:00:00Z"/>
                """));
    }

    @Test
    void validatesAssertionsAndTheirRequiredChildren() {
        assertTrue(valid(SchemaKind.ASSERTION, assertion("<saml:AuthnContext><saml:AuthnContextClassRef>urn:example:loa</saml:AuthnContextClassRef></saml:AuthnContext>")));
        assertFalse(valid(SchemaKind.ASSERTION, assertion("")));
    }

    private boolean valid(SchemaKind kind, String xml) {
        return SamlSchemaValidation.isValid(SecureXml.parse(xml.getBytes(StandardCharsets.UTF_8)).getDocumentElement(), kind);
    }

    private String assertion(String authnContext) {
        return """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <saml:Issuer>https://idp.example</saml:Issuer>
                  <saml:AuthnStatement AuthnInstant="2026-08-29T00:00:00Z">%s</saml:AuthnStatement>
                </saml:Assertion>
                """.formatted(authnContext);
    }
}
