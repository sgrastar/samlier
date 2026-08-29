package org.samlier.saml.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.xml.security.signature.XMLSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.saml.SamlTestFixtures;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.normal.OpenSamlReader;
import org.samlier.saml.normal.SecureXml;

class MetadataServiceTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void emitsSignedAllInOneMetadata() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var xml = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock)
                .generate(plan);
        var parsed = new OpenSamlReader().read(xml);
        assertEquals("EntityDescriptor", parsed.openSamlObject().getElementQName().getLocalPart());
        var document = SecureXml.parse(xml);
        document.getDocumentElement().setIdAttribute("ID", true);
        assertEquals(1, document.getElementsByTagNameNS(MetadataService.MD, "SPSSODescriptor").getLength());
        assertEquals(1, document.getElementsByTagNameNS(MetadataService.MD, "IDPSSODescriptor").getLength());
        var signatureElement = (org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0);
        var signature = new XMLSignature(signatureElement, "");
        assertTrue(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()));
    }

    @Test
    void emitsCryptographicallyValidMetadataVariantsWithCorrelatedEndpoints() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var service = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock);
        var runId = "run_0123456789ABCDEFGHJKMNPQRS";

        for (var variant : MetadataService.Variant.values()) {
            if (variant == MetadataService.Variant.BASELINE) continue;
            var xml = service.generate(plan, variant, runId);
            var document = SecureXml.parse(xml);
            document.getDocumentElement().setIdAttribute("ID", true);
            var signatureElement = (org.w3c.dom.Element) document
                    .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0);
            assertTrue(new XMLSignature(signatureElement, "")
                    .checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()), variant.name());
            assertTrue(new String(xml, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("mdv=" + variant.id() + "&amp;run=" + runId), variant.name());
        }
    }

    @Test
    void noKeyInfoVariantOmitsOnlyKeyInfoFromTheSignature() {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var xml = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock)
                .generate(plan, MetadataService.Variant.NO_KEY_INFO, "run_0123456789ABCDEFGHJKMNPQRS");
        var document = SecureXml.parse(xml);
        var signature = (org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0);
        assertEquals(0, signature.getElementsByTagNameNS(MetadataService.DS, "KeyInfo").getLength());
        assertTrue(document.getElementsByTagNameNS(MetadataService.DS, "KeyInfo").getLength() > 0,
                "metadata role KeyDescriptors remain intact");
    }
}
