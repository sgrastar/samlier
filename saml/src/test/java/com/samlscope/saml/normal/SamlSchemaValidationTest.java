package com.samlscope.saml.normal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.saml.normal.SamlSchemaValidation.SchemaKind;

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
    void validatesXmlEncryption11ParametersInsideAnEncryptedAssertion() {
        assertTrue(valid(SchemaKind.PROTOCOL, """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                  xmlns:xenc="http://www.w3.org/2001/04/xmlenc#"
                  xmlns:xenc11="http://www.w3.org/2009/xmlenc11#"
                  ID="_response" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <samlp:Status>
                    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                  </samlp:Status>
                  <saml:EncryptedAssertion>
                    <xenc:EncryptedData Type="http://www.w3.org/2001/04/xmlenc#Element">
                      <xenc:EncryptionMethod Algorithm="http://www.w3.org/2009/xmlenc11#aes256-gcm"/>
                      <ds:KeyInfo>
                        <xenc:EncryptedKey>
                          <xenc:EncryptionMethod Algorithm="http://www.w3.org/2009/xmlenc11#rsa-oaep">
                            <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                            <xenc11:MGF Algorithm="http://www.w3.org/2009/xmlenc11#mgf1sha256"/>
                          </xenc:EncryptionMethod>
                          <xenc:CipherData><xenc:CipherValue>AA==</xenc:CipherValue></xenc:CipherData>
                        </xenc:EncryptedKey>
                      </ds:KeyInfo>
                      <xenc:CipherData><xenc:CipherValue>AA==</xenc:CipherValue></xenc:CipherData>
                    </xenc:EncryptedData>
                  </saml:EncryptedAssertion>
                </samlp:Response>
                """));
    }

    @Test
    void validatesAssertionsAndTheirRequiredChildren() {
        assertTrue(valid(SchemaKind.ASSERTION, assertion("<saml:AuthnContext><saml:AuthnContextClassRef>urn:example:loa</saml:AuthnContextClassRef></saml:AuthnContext>")));
        assertFalse(valid(SchemaKind.ASSERTION, assertion("")));
    }

    @Test
    void reportsSchemaTypedStringElementsAndAttributes() {
        var element = SecureXml.parse("""
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_response" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                    <samlp:StatusMessage>accepted</samlp:StatusMessage>
                  </samlp:Status>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8)).getDocumentElement();

        var inspection = SamlSchemaValidation.inspectStringValues(element, SchemaKind.PROTOCOL);

        assertTrue(inspection.schemaValid());
        assertTrue(inspection.values().stream().anyMatch(value -> value.attribute()
                && value.path().endsWith("/@Version") && value.value().equals("2.0")));
        assertEquals(List.of("accepted"), inspection.values().stream()
                .filter(value -> !value.attribute() && value.path().endsWith("}StatusMessage"))
                .map(SamlSchemaValidation.TypedStringValue::value).toList());
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
