package org.samlier.saml.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.normal.SamlException;

class TargetMetadataParserTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void extractsOnlySigningAndUnspecifiedCertificatesAndDeduplicatesThem() throws Exception {
        var certificate = new FilePlanKeyStore(
                directory, Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC))
                .getOrCreate("plan_0123456789ABCDEFGHJKMNPQRS").certificate();
        var encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        var metadata = metadata("""
                <md:KeyDescriptor use="signing"><ds:KeyInfo><ds:X509Data>
                  <ds:X509Certificate>%s</ds:X509Certificate>
                </ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
                <md:KeyDescriptor><ds:KeyInfo><ds:X509Data>
                  <ds:X509Certificate>%s</ds:X509Certificate>
                </ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
                <md:KeyDescriptor use="encryption"><ds:KeyInfo><ds:X509Data>
                  <ds:X509Certificate>%s</ds:X509Certificate>
                </ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
                """.formatted(encoded, encoded, encoded));

        var parsed = new TargetMetadataParser().parse(metadata, "https://idp.example/entity");

        assertEquals(1, parsed.signingCertificates().size());
        assertEquals(certificate, parsed.signingCertificates().getFirst());
    }

    @Test
    void rejectsMalformedSigningCertificateButIgnoresMalformedEncryptionOnlyCertificate() {
        var malformedSigning = metadata("""
                <md:KeyDescriptor use="signing"><ds:KeyInfo><ds:X509Data>
                  <ds:X509Certificate>not-base64</ds:X509Certificate>
                </ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
                """);
        assertThrows(SamlException.class, () -> new TargetMetadataParser().parse(
                malformedSigning, "https://idp.example/entity"));

        var malformedEncryption = metadata("""
                <md:KeyDescriptor use="encryption"><ds:KeyInfo><ds:X509Data>
                  <ds:X509Certificate>not-base64</ds:X509Certificate>
                </ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
                """);
        assertEquals(0, new TargetMetadataParser().parse(
                malformedEncryption, "https://idp.example/entity").signingCertificates().size());
    }

    private byte[] metadata(String keys) {
        return ("""
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                    xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="https://idp.example/entity">
                  <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    %s
                    <md:SingleSignOnService Binding="urn:test:binding" Location="https://idp.example/sso"/>
                  </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """).formatted(keys).getBytes(StandardCharsets.UTF_8);
    }
}
