package org.samlier.saml.normal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
