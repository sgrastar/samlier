package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.crypto.FilePlanKeyStore;

class TargetMetadataObservationTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String SAML2 = "urn:oasis:names:tc:SAML:2.0:protocol";
    @TempDir java.nio.file.Path directory;

    @Test
    void detectsOverlappingSameTypeRolesWithoutTreatingOneRoleAsAnOverlap() {
        assertOutcome("IIP-MD05-a7-idp-01", Outcome.SATISFIED_WITH_NOTE,
                metadata(role("IDPSSODescriptor", SAML2, "")));
        assertOutcome("IIP-MD05-a7-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2, "")
                        + role("IDPSSODescriptor", SAML2, "")));
        assertOutcome("IIP-MD05-a7-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, "")
                        + role("IDPSSODescriptor", "urn:other:protocol", "")));
        assertOutcome("IIP-MD05-a7-idp-01", Outcome.SATISFIED_WITH_NOTE,
                metadata(genericRole("ext:One", SAML2) + genericRole("ext:Two", SAML2)));
        assertOutcome("IIP-MD05-a7-idp-01", Outcome.VIOLATED,
                metadata(genericRole("ext:One", SAML2) + genericRole("alt:One", SAML2)));
    }

    @Test
    void everySamlRoleMustAdvertiseTheSamlTwoProtocol() {
        assertOutcome("IIP-MD05-a9-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, "")));
        assertOutcome("IIP-MD05-a9-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", "urn:other:protocol", "")));
        assertOutcome("IIP-MD05-a9-idp-01", Outcome.SATISFIED_WITH_NOTE,
                metadata(role("RoleDescriptor", "urn:non-saml:protocol", "")));
    }

    @Test
    void oneDirectionEndpointsMustNotCarryResponseLocation() {
        assertOutcome("IIP-MD05-ab-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:SingleSignOnService Binding=\"urn:test\" Location=\"https://idp.example/sso\"/>")));
        assertOutcome("IIP-MD05-ab-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:SingleSignOnService Binding=\"urn:test\" Location=\"https://idp.example/sso\" ResponseLocation=\"https://idp.example/response\"/>")));
    }

    @Test
    void keyInfoRequiresAKeyValueOrExactlyOneCertificateRepresentation() throws Exception {
        var certificate = certificate();
        assertOutcome("IIP-MD05-c9-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, keyDescriptor(
                        "<ds:X509Data><ds:X509Certificate>" + certificate + "</ds:X509Certificate></ds:X509Data>"))));
        assertOutcome("IIP-MD05-c9-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2, keyDescriptor("<ds:KeyName>only-a-hint</ds:KeyName>"))));
        assertOutcome("IIP-MD05-ca-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2, keyDescriptor(
                        "<ds:X509Data><ds:X509Certificate>" + certificate + "</ds:X509Certificate>"
                                + "<ds:X509Certificate>" + certificate + "</ds:X509Certificate></ds:X509Data>"))));
        assertOutcome("IIP-MD05-ca-idp-01", Outcome.SATISFIED_WITH_NOTE,
                metadata("<ds:Signature><ds:KeyInfo><ds:X509Data>"
                        + "<ds:X509Certificate>" + certificate + "</ds:X509Certificate>"
                        + "<ds:X509Certificate>" + certificate + "</ds:X509Certificate>"
                        + "</ds:X509Data></ds:KeyInfo></ds:Signature>"
                        + role("IDPSSODescriptor", SAML2, "")));
    }

    @Test
    void publisherCertificateValidityIsObservedWithoutChangingConsumerAcceptance() throws Exception {
        var certificate = certificate();
        assertOutcome("IIP-MD05-ce-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, keyDescriptor(
                        "<ds:X509Data><ds:X509Certificate>" + certificate + "</ds:X509Certificate></ds:X509Data>"))));
        var outcome = TargetMetadataObservation.evaluate(
                "IIP-MD05-ce-idp-01",
                metadata(role("IDPSSODescriptor", SAML2, keyDescriptor(
                        "<ds:X509Data><ds:X509Certificate>" + certificate + "</ds:X509Certificate></ds:X509Data>"))),
                Instant.parse("2126-08-30T00:00:00Z")).orElseThrow();
        assertEquals(Outcome.VIOLATED, outcome.outcome());
    }

    @Test
    void encryptionDescriptorListsDataAndKeyAlgorithms() {
        var both = encryptionMethod("http://www.w3.org/2009/xmlenc11#aes128-gcm")
                + encryptionMethod("http://www.w3.org/2009/xmlenc11#rsa-oaep");
        assertOutcome("IIP-MD05-e1-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, encryptionKeyDescriptor("encryption", both))));
        assertOutcome("IIP-MD05-e1-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        encryptionKeyDescriptor("encryption", encryptionMethod(
                                "http://www.w3.org/2009/xmlenc11#aes128-gcm")))));
        assertOutcome("IIP-MD05-e1-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2, encryptionKeyDescriptor("", both))));
        assertOutcome("IIP-MD05-e1-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2, encryptionKeyDescriptor("", ""))));
    }

    @Test
    void algorithmUrisAreCheckedWithoutDelegatingToSchemaValidation() {
        assertOutcome("IIP-MD05-e4-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2, encryptionKeyDescriptor("encryption",
                        "<md:EncryptionMethod/>"))));
        assertOutcome("IIP-MD05-ec-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><alg:DigestMethod Algorithm=\"urn:digest\"/><alg:SigningMethod/></md:Extensions>")));
    }

    @Test
    void everyEntityPublishesBothSignatureCapabilityKinds() {
        var capabilities = "<md:Extensions><alg:DigestMethod Algorithm=\"urn:digest\"/>"
                + "<alg:SigningMethod Algorithm=\"urn:signature\"/></md:Extensions>";
        assertOutcome("IIP-MD05-e6-idp-01", Outcome.SATISFIED,
                metadata(capabilities + role("IDPSSODescriptor", SAML2, "")));
        assertOutcome("IIP-MD05-e6-idp-01", Outcome.VIOLATED,
                metadata("<md:Extensions><alg:DigestMethod Algorithm=\"urn:digest\"/></md:Extensions>"
                        + role("IDPSSODescriptor", SAML2, "")));
        var twoEntities = ("""
                <md:EntitiesDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                  xmlns:alg="urn:oasis:names:tc:SAML:metadata:algsupport">
                  %s
                  %s
                </md:EntitiesDescriptor>
                """).formatted(
                entity("https://one.example", capabilities + role("IDPSSODescriptor", SAML2, "")),
                entity("https://two.example", role("IDPSSODescriptor", SAML2, "")));
        assertOutcome("IIP-MD05-e6-idp-01", Outcome.VIOLATED,
                twoEntities.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uiInfoPlacementContentCardinalityAndLanguagesAreObserved() {
        assertOutcome("IIP-MD05-f1-idp-01", Outcome.SATISFIED_WITH_NOTE, metadata(""));
        assertOutcome("IIP-MD05-f1-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo><ui:DisplayName xml:lang=\"en\">Example</ui:DisplayName></ui:UIInfo></md:Extensions>")));
        assertOutcome("IIP-MD05-f1-idp-01", Outcome.VIOLATED,
                metadata("<md:Extensions><ui:UIInfo><ui:DisplayName xml:lang=\"en\">Example</ui:DisplayName></ui:UIInfo></md:Extensions>"));

        assertOutcome("IIP-MD05-f2-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo/></md:Extensions>")));
        assertOutcome("IIP-MD05-f2-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo><ext:Custom/></ui:UIInfo></md:Extensions>")));

        assertOutcome("IIP-MD05-f3-sp-01", Outcome.VIOLATED,
                metadata(role("SPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo/><ui:UIInfo/></md:Extensions>")));
        assertOutcome("IIP-MD05-f3-sp-01", Outcome.SATISFIED,
                metadata(role("SPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo/></md:Extensions>")));

        assertOutcome("IIP-MD05-f4-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo>"
                                + "<ui:DisplayName xml:lang=\"en\">One</ui:DisplayName>"
                                + "<ui:DisplayName xml:lang=\"en\">Two</ui:DisplayName>"
                                + "</ui:UIInfo></md:Extensions>")));
        assertOutcome("IIP-MD05-f4-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo>"
                                + "<ui:DisplayName xml:lang=\"en\">Name</ui:DisplayName>"
                                + "<ui:Description xml:lang=\"en\">Description</ui:Description>"
                                + "</ui:UIInfo></md:Extensions>")));
    }

    @Test
    void discoHintsPlacementContentAndCardinalityAreObservedForIdpPublishers() {
        assertOutcome("IIP-MD05-fc-idp-01", Outcome.SATISFIED_WITH_NOTE, metadata(""));
        assertOutcome("IIP-MD05-fc-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints><ui:DomainHint>example.test</ui:DomainHint></ui:DiscoHints></md:Extensions>")));
        assertOutcome("IIP-MD05-fc-idp-01", Outcome.VIOLATED,
                metadata(role("SPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints><ui:DomainHint>example.test</ui:DomainHint></ui:DiscoHints></md:Extensions>")));

        assertOutcome("IIP-MD05-fd-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints/></md:Extensions>")));
        assertOutcome("IIP-MD05-fd-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints><ext:Custom/></ui:DiscoHints></md:Extensions>")));

        assertOutcome("IIP-MD05-fe-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints/><ui:DiscoHints/></md:Extensions>")));
        assertOutcome("IIP-MD05-fe-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:DiscoHints/></md:Extensions>")));
    }

    @Test
    void everyPublishedLogoCarriesBothDimensions() {
        assertOutcome("IIP-MD05-fk-idp-01", Outcome.SATISFIED_WITH_NOTE, metadata(""));
        assertOutcome("IIP-MD05-fk-idp-01", Outcome.SATISFIED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo><ui:Logo height=\"32\" width=\"64\">https://example.test/logo.svg</ui:Logo></ui:UIInfo></md:Extensions>")));
        assertOutcome("IIP-MD05-fk-idp-01", Outcome.VIOLATED,
                metadata(role("IDPSSODescriptor", SAML2,
                        "<md:Extensions><ui:UIInfo><ui:Logo height=\"32\">https://example.test/logo.svg</ui:Logo></ui:UIInfo></md:Extensions>")));
    }

    @Test
    void malformedMetadataAndUnsupportedCasesRemainManual() {
        assertTrue(TargetMetadataObservation.evaluate(
                "IIP-MD05-e6-idp-01", "<broken".getBytes(StandardCharsets.UTF_8), NOW).isEmpty());
        assertTrue(TargetMetadataObservation.evaluate(
                "IIP-MD05-a6-idp-01", metadata(""), NOW).isEmpty());
        assertTrue(TargetMetadataObservation.supports("IIP-MD05-f1-sp-01"));
        assertFalse(TargetMetadataObservation.supports("IIP-MD05-fc-sp-01"));
    }

    @Test
    void evidenceIsBoundToTheExactMetadataBytes() {
        var first = TargetMetadataObservation.evaluate(
                "IIP-MD05-a9-idp-01", metadata(role("IDPSSODescriptor", SAML2, "")), NOW)
                .orElseThrow().evidence().getFirst().reference();
        var second = TargetMetadataObservation.evaluate(
                "IIP-MD05-a9-idp-01",
                metadata(role("IDPSSODescriptor", SAML2, "<!--different bytes-->")), NOW)
                .orElseThrow().evidence().getFirst().reference();

        assertTrue(first.matches("sha256:[0-9a-f]{64}"));
        assertNotEquals(first, second);
    }

    @Test
    void automaticWrapperPreservesBothApprovedManualPrompts() {
        var evidence = new AttestedOutcomeTestCase(
                "IIP-MD05-a7-idp-01", org.samlier.core.plan.TargetRole.IDP,
                "evidence-key", "Review evidence", Duration.ofHours(1),
                List.of(AttestationOption.of("satisfied", Outcome.SATISFIED, "satisfied")));
        var fallback = new ConfigurationGateTestCase(
                evidence, "config-key", "Prepare configuration", Duration.ofHours(1),
                org.samlier.core.caseexec.ConfigurationFailureSemantics.TEST_PRECONDITION);
        var wrapper = new AutoConfigurationEvidenceTestCase(fallback, ignored -> null);

        assertEquals("Prepare configuration", wrapper.instructionEn());
        assertEquals("Review evidence", wrapper.promptEn());
        assertEquals(List.of("satisfied"), wrapper.options().stream().map(AttestationOption::value).toList());
    }

    private void assertOutcome(String caseId, Outcome expected, byte[] metadata) {
        assertEquals(expected, TargetMetadataObservation.evaluate(caseId, metadata, NOW).orElseThrow().outcome());
    }

    private byte[] metadata(String contents) {
        return entity("https://idp.example/entity", contents).getBytes(StandardCharsets.UTF_8);
    }

    private String entity(String entityId, String contents) {
        return """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                  xmlns:alg="urn:oasis:names:tc:SAML:metadata:algsupport"
                  xmlns:ui="urn:oasis:names:tc:SAML:metadata:ui"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:ext="urn:example:role" xmlns:alt="urn:example:role"
                  entityID="%s">%s</md:EntityDescriptor>
                """.formatted(entityId, contents);
    }

    private String role(String localName, String protocols, String children) {
        return "<md:" + localName + " protocolSupportEnumeration=\"" + protocols + "\">"
                + children + "</md:" + localName + ">";
    }

    private String genericRole(String type, String protocols) {
        return "<md:RoleDescriptor xsi:type=\"" + type
                + "\" protocolSupportEnumeration=\"" + protocols + "\"/>";
    }

    private String keyDescriptor(String keyInfo) {
        return "<md:KeyDescriptor use=\"signing\"><ds:KeyInfo>" + keyInfo + "</ds:KeyInfo></md:KeyDescriptor>";
    }

    private String encryptionKeyDescriptor(String use, String methods) {
        var attribute = use.isBlank() ? "" : " use=\"" + use + "\"";
        return "<md:KeyDescriptor" + attribute + "><ds:KeyInfo><ds:KeyValue/></ds:KeyInfo>"
                + methods + "</md:KeyDescriptor>";
    }

    private String encryptionMethod(String algorithm) {
        return "<md:EncryptionMethod Algorithm=\"" + algorithm + "\"/>";
    }

    private String certificate() throws Exception {
        var certificate = new FilePlanKeyStore(directory, Clock.fixed(NOW, ZoneOffset.UTC))
                .getOrCreate("plan_0123456789ABCDEFGHJKMNPQRS").certificate();
        return Base64.getEncoder().encodeToString(certificate.getEncoded());
    }
}
