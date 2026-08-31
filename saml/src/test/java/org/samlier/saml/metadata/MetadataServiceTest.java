package org.samlier.saml.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        var acs = document.getElementsByTagNameNS(MetadataService.MD, "AssertionConsumerService");
        assertEquals(4, acs.getLength());
        assertEquals("0", ((org.w3c.dom.Element) acs.item(0)).getAttribute("index"));
        assertEquals("1", ((org.w3c.dom.Element) acs.item(1)).getAttribute("index"));
        assertEquals(MetadataService.REDIRECT,
                ((org.w3c.dom.Element) acs.item(3)).getAttribute("Binding"));
        assertEquals("3", ((org.w3c.dom.Element) acs.item(3)).getAttribute("index"));
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
            var signatures = document.getElementsByTagNameNS(MetadataService.DS, "Signature");
            if (variant == MetadataService.Variant.UNSIGNED) {
                assertEquals(0, signatures.getLength(), variant.name());
            } else {
                var signature = new XMLSignature((org.w3c.dom.Element) signatures.item(0), "");
                if (variant == MetadataService.Variant.BAD_SIGNATURE) {
                    assertFalse(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()),
                            variant.name());
                } else if (variant == MetadataService.Variant.SIGNED_OTHER_KEY
                        || variant == MetadataService.Variant.SIGNED_OTHER_KEY_PRIMARY_KEYINFO) {
                    assertTrue(signature.checkSignatureValue(
                            keyStore.getOrCreate(plan.id(), "metadata-other").certificate()), variant.name());
                    assertFalse(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()),
                            variant.name());
                } else {
                    assertTrue(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()),
                            variant.name());
                }
            }
            assertTrue(new String(xml, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("mdv=" + variant.id() + "&amp;run=" + runId), variant.name());
        }
    }

    @Test
    void pollingFixturesUseDeterministicDistinctRoleKeysThatMatchSignedRequests() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var service = new MetadataService(
                URI.create("https://peer.example"), keyStore, new XmlSigner(), clock);
        var control = service.credentialsForPollingVariant(plan, MetadataService.Variant.CONTROL);
        var extension = service.credentialsForPollingVariant(
                plan, MetadataService.Variant.UNKNOWN_EXTENSION);

        assertNotEquals(
                java.util.Base64.getEncoder().encodeToString(control.certificate().getEncoded()),
                java.util.Base64.getEncoder().encodeToString(extension.certificate().getEncoded()));
        assertEquals(
                java.util.Base64.getEncoder().encodeToString(control.certificate().getEncoded()),
                java.util.Base64.getEncoder().encodeToString(service.credentialsForPollingVariant(
                        plan, MetadataService.Variant.CONTROL).certificate().getEncoded()));

        var document = SecureXml.parse(service.generatePolling(
                plan, MetadataService.Variant.CONTROL, "run_0123456789ABCDEFGHJKMNPQRS"));
        document.getDocumentElement().setIdAttribute("ID", true);
        var signature = new XMLSignature((org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0), "");
        assertTrue(signature.checkSignatureValue(control.certificate()));
        var certificateText = document.getElementsByTagNameNS(MetadataService.DS, "X509Certificate")
                .item(0).getTextContent().replaceAll("\\s+", "");
        assertEquals(java.util.Base64.getEncoder().encodeToString(control.certificate().getEncoded()),
                certificateText);
    }

    @Test
    void preloadedCampaignCombinesOnlyCompatiblePositiveFixturesUnderOneSignature() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var service = new MetadataService(
                URI.create("https://peer.example"), keyStore, new XmlSigner(), clock);
        var runId = "run_0123456789ABCDEFGHJKMNPQRS";

        var document = SecureXml.parse(service.generatePreloadedCampaign(plan, runId));
        var root = document.getDocumentElement();
        root.setIdAttribute("ID", true);
        assertEquals("EntitiesDescriptor", root.getLocalName());
        assertEquals(1, document.getElementsByTagNameNS(MetadataService.DS, "Signature").getLength(),
                "the aggregate has one trust root; child fixture signatures must not survive");
        var signature = new XMLSignature((org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0), "");
        assertTrue(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()));

        var xml = new String(SecureXml.serialize(document), java.nio.charset.StandardCharsets.UTF_8);
        for (var variant : MetadataService.preloadedCampaignVariants()) {
            var expectedEntity = service.preloadedEntityId(plan, variant);
            int matches = 0;
            var entities = document.getElementsByTagNameNS(MetadataService.MD, "EntityDescriptor");
            for (int index = 0; index < entities.getLength(); index++) {
                if (expectedEntity.equals(((org.w3c.dom.Element) entities.item(index)).getAttribute("entityID"))) {
                    matches++;
                }
            }
            assertEquals(1, matches, variant.id());
            assertTrue(xml.contains("mdv=" + variant.id() + "&amp;run=" + runId), variant.id());
        }
        var roles = document.getElementsByTagNameNS(MetadataService.MD, "SPSSODescriptor");
        for (int index = 0; index < roles.getLength(); index++) {
            assertEquals("true", ((org.w3c.dom.Element) roles.item(index))
                    .getAttribute("AuthnRequestsSigned"));
        }
        assertFalse(MetadataService.preloadedCampaignVariants().contains(MetadataService.Variant.UNSIGNED));
        assertFalse(MetadataService.preloadedCampaignVariants().contains(MetadataService.Variant.BAD_SIGNATURE));
        assertFalse(MetadataService.preloadedCampaignVariants().contains(MetadataService.Variant.EXPIRED));
        assertFalse(MetadataService.preloadedCampaignVariants().contains(
                MetadataService.Variant.CONFLICTING_DUPLICATE_ENTITY_IDS));
    }

    @Test
    void keyDescriptorFixturesExposeOnlyTheIntendedStandardKeyForms() {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var service = new MetadataService(
                URI.create("https://peer.example"), new FilePlanKeyStore(directory, clock),
                new XmlSigner(), clock);
        var plan = SamlTestFixtures.idpPlan();
        var runId = "run_0123456789ABCDEFGHJKMNPQRS";

        var keyValue = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.KEYVALUE_ONLY, runId));
        assertEquals(2, keyValue.getElementsByTagNameNS(MetadataService.DS, "RSAKeyValue").getLength());
        assertEquals(1, keyValue.getElementsByTagNameNS(MetadataService.DS, "X509Data").getLength(),
                "only the root signature KeyInfo retains X509Data");

        var omitted = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.KEY_USE_OMITTED, runId));
        var descriptors = omitted.getElementsByTagNameNS(MetadataService.MD, "KeyDescriptor");
        assertFalse(((org.w3c.dom.Element) descriptors.item(0)).hasAttribute("use"));

        var three = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.THREE_SIGNING_KEYS, runId));
        var sp = (org.w3c.dom.Element) three
                .getElementsByTagNameNS(MetadataService.MD, "SPSSODescriptor").item(0);
        int signing = 0;
        var keys = sp.getElementsByTagNameNS(MetadataService.MD, "KeyDescriptor");
        for (int i = 0; i < keys.getLength(); i++) {
            if ("signing".equals(((org.w3c.dom.Element) keys.item(i)).getAttribute("use"))) signing++;
        }
        assertEquals(3, signing);
    }

    @Test
    void outOfBandKeyFixtureDoesNotAdvertiseTheActualSignatureKey() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var xml = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock)
                .generate(plan, MetadataService.Variant.SIGNED_OTHER_KEY_PRIMARY_KEYINFO,
                        "run_0123456789ABCDEFGHJKMNPQRS");
        var document = SecureXml.parse(xml);
        document.getDocumentElement().setIdAttribute("ID", true);
        var signatureElement = (org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0);
        var signature = new XMLSignature(signatureElement, "");
        assertTrue(signature.checkSignatureValue(keyStore.getOrCreate(plan.id(), "metadata-other").certificate()));
        var advertised = signatureElement.getElementsByTagNameNS(MetadataService.DS, "X509Certificate")
                .item(0).getTextContent().replaceAll("\\s", "");
        assertEquals(java.util.Base64.getEncoder().encodeToString(
                keyStore.getOrCreate(plan.id()).certificate().getEncoded()), advertised);
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

    @Test
    void structuralFixturesPutTheCorrelatedEntityAtTheRequiredDepthAndPreserveSignatures() {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var service = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock);
        var runId = "run_0123456789ABCDEFGHJKMNPQRS";

        var fifty = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.ENTITIES_ROOT_FIFTY, runId));
        assertEquals("EntitiesDescriptor", fifty.getDocumentElement().getLocalName());
        assertEquals(50, fifty.getElementsByTagNameNS(MetadataService.MD, "EntityDescriptor").getLength());
        var last = (org.w3c.dom.Element) fifty
                .getElementsByTagNameNS(MetadataService.MD, "EntityDescriptor").item(49);
        assertEquals("https://peer.example/p/" + plan.id(), last.getAttribute("entityID"));

        var nested = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.NESTED_ENTITIES, runId));
        assertEquals(2, nested.getElementsByTagNameNS(MetadataService.MD, "EntitiesDescriptor").getLength());

        var cacheOnly = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.ENTITIES_CACHE_DURATION, runId)).getDocumentElement();
        assertEquals("PT1H", cacheOnly.getAttribute("cacheDuration"));
        assertTrue(!cacheOnly.hasAttribute("validUntil"));
    }

    @Test
    void extensionFixturesCoverEntityRoleEndpointAndInvalidNamespaceCases() {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var service = new MetadataService(
                URI.create("https://peer.example"), new FilePlanKeyStore(directory, clock),
                new XmlSigner(), clock);
        var plan = SamlTestFixtures.idpPlan();
        var runId = "run_0123456789ABCDEFGHJKMNPQRS";

        var entity = SecureXml.parse(service.generate(plan, MetadataService.Variant.UNKNOWN_EXTENSION, runId));
        assertEquals(1, entity.getElementsByTagNameNS(
                "urn:samlier:test:metadata-extension", "Probe").getLength());
        var role = SecureXml.parse(service.generate(plan, MetadataService.Variant.UNKNOWN_ROLE_EXTENSION, runId));
        var sp = (org.w3c.dom.Element) role
                .getElementsByTagNameNS(MetadataService.MD, "SPSSODescriptor").item(0);
        assertEquals(1, sp.getElementsByTagNameNS(
                "urn:samlier:test:metadata-extension", "Probe").getLength());
        var endpoint = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.UNKNOWN_ENDPOINT_EXTENSION, runId));
        var acs = (org.w3c.dom.Element) endpoint
                .getElementsByTagNameNS(MetadataService.MD, "AssertionConsumerService").item(0);
        assertEquals("endpoint-attribute", acs.getAttributeNS(
                "urn:samlier:test:metadata-extension", "probe"));
        var invalid = SecureXml.parse(service.generate(
                plan, MetadataService.Variant.INVALID_SAML_EXTENSION, runId));
        assertEquals(1, invalid.getElementsByTagNameNS(MetadataService.SAML, "Attribute").getLength());
    }

    @Test
    void secondaryIdpUsesADistinctEntityAndSigningKey() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var plan = SamlTestFixtures.idpPlan();
        var xml = new MetadataService(URI.create("https://peer.example"), keyStore, new XmlSigner(), clock)
                .generateSecondaryIdp(plan);
        var document = SecureXml.parse(xml);
        var root = document.getDocumentElement();
        root.setIdAttribute("ID", true);
        assertEquals("https://peer.example/p/plan_0123456789ABCDEFGHJKMNPQRS/idp/secondary",
                root.getAttribute("entityID"));
        assertEquals(0, document.getElementsByTagNameNS(MetadataService.MD, "SPSSODescriptor").getLength());
        assertEquals(1, document.getElementsByTagNameNS(MetadataService.MD, "IDPSSODescriptor").getLength());
        var secondary = keyStore.getOrCreate(plan.id(), "secondary-idp").certificate();
        assertNotEquals(keyStore.getOrCreate(plan.id()).certificate(), secondary);
        var signatureElement = (org.w3c.dom.Element) document
                .getElementsByTagNameNS(MetadataService.DS, "Signature").item(0);
        assertTrue(new XMLSignature(signatureElement, "").checkSignatureValue(secondary));
    }
}
