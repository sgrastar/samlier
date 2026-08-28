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
}
