package com.samlscope.saml.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.samlscope.saml.normal.SamlErrorProbeRequestFactory.Probe;

class SamlErrorProbeRequestFactoryTest {
    private final SamlErrorProbeRequestFactory factory = new SamlErrorProbeRequestFactory();

    @Test
    void buildsSchemaValidRequestsWithRegisteredResponseLocation() {
        for (var probe : Probe.values()) {
            if (probe == Probe.DTD_AUTHN_REQUEST || probe == Probe.DTD_EXTERNAL_ENTITY_AUTHN_REQUEST) {
                continue;
            }
            var document = SecureXml.parse(factory.build(
                    probe,
                    "_request",
                    URI.create("https://idp.example/sso"),
                    "https://suite.example/sp",
                    URI.create("https://suite.example/acs"),
                    Instant.parse("2026-08-29T00:00:00Z")));

            assertTrue(SamlSchemaValidation.isValid(
                    document.getDocumentElement(), SamlSchemaValidation.SchemaKind.PROTOCOL), probe.name());
            if (probe == Probe.ACS_SELECTION_OMITTED) {
                assertFalse(document.getDocumentElement().hasAttribute("AssertionConsumerServiceURL"));
                assertFalse(document.getDocumentElement().hasAttribute("ProtocolBinding"));
            } else {
                assertEquals("https://suite.example/acs",
                        document.getDocumentElement().getAttribute("AssertionConsumerServiceURL"));
            }
        }
    }

    @Test
    void buildsIntentionalDtdFixturesWithoutParsingThemInTheSuite() {
        for (var probe : java.util.List.of(
                Probe.DTD_AUTHN_REQUEST, Probe.DTD_EXTERNAL_ENTITY_AUTHN_REQUEST)) {
            var xml = new String(factory.build(
                    probe, "_request", URI.create("https://idp.example/sso"),
                    "https://suite.example/sp", URI.create("https://suite.example/acs"),
                    Instant.parse("2026-08-29T00:00:00Z")), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("<!DOCTYPE samlp:AuthnRequest"));
            assertThrows(SamlException.class, () -> SecureXml.parse(
                    xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    @Test
    void eachProbeChangesOnlyItsApprovedTrigger() {
        var unknown = request(Probe.UNKNOWN_NAMEID_FORMAT);
        var context = request(Probe.UNSATISFIABLE_AUTHN_CONTEXT);
        var passive = request(Probe.PASSIVE_WITHOUT_SESSION);

        assertEquals(1, unknown.getElementsByTagNameNS(protocol(), "NameIDPolicy").getLength());
        assertEquals(0, unknown.getElementsByTagNameNS(protocol(), "RequestedAuthnContext").getLength());
        assertFalse(unknown.getDocumentElement().hasAttribute("IsPassive"));
        assertEquals(1, context.getElementsByTagNameNS(protocol(), "RequestedAuthnContext").getLength());
        assertEquals("true", passive.getDocumentElement().getAttribute("IsPassive"));
        assertEquals("2026-08-29T00:00:00.000123456Z",
                request(Probe.SUBMILLISECOND_ISSUE_INSTANT).getDocumentElement().getAttribute("IssueInstant"));
    }

    private org.w3c.dom.Document request(Probe probe) {
        return SecureXml.parse(factory.build(
                probe, "_request", URI.create("https://idp.example/sso"), "https://suite.example/sp",
                URI.create("https://suite.example/acs"), Instant.parse("2026-08-29T00:00:00Z")));
    }

    private String protocol() { return "urn:oasis:names:tc:SAML:2.0:protocol"; }
}
