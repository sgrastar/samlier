package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class MetadataSignatureProfileCaseTest {
    @Test
    void acceptsTheApprovedMetadataSignatureProfile() {
        for (var rule : MetadataSignatureProfileCase.Rule.values()) {
            assertEquals(Outcome.SATISFIED, evaluate(rule, signedMetadata("_root", "#_root", transforms(
                    "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                    "http://www.w3.org/2001/10/xml-exc-c14n#"))).outcome(), rule.name());
        }
    }

    @Test
    void rejectsMissingIdWrongReferenceAndUnauthorizedTransform() {
        assertEquals(Outcome.VIOLATED, evaluate(
                MetadataSignatureProfileCase.Rule.SIGNED_ELEMENT_ID,
                signedMetadata("", "#_root", transforms(
                        "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                        "http://www.w3.org/2001/10/xml-exc-c14n#"))).outcome());
        assertEquals(Outcome.VIOLATED, evaluate(
                MetadataSignatureProfileCase.Rule.SINGLE_ROOT_REFERENCE,
                signedMetadata("_root", "#other", transforms(
                        "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                        "http://www.w3.org/2001/10/xml-exc-c14n#"))).outcome());
        assertEquals(Outcome.VIOLATED, evaluate(
                MetadataSignatureProfileCase.Rule.ALLOWED_TRANSFORMS,
                signedMetadata("_root", "#_root", transforms(
                        "http://www.w3.org/TR/1999/REC-xpath-19991116"))).outcome());
    }

    @Test
    void unsignedMetadataDoesNotFabricateAProducerViolation() {
        var unsigned = "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" ID=\"_root\"/>"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(Outcome.SATISFIED_WITH_NOTE,
                new MetadataSignatureProfileCase(MetadataSignatureProfileCase.Rule.ENVELOPED)
                        .evaluate(unsigned).outcome());
        var rsa = new MetadataSignatureProfileCase(MetadataSignatureProfileCase.Rule.RSA_SHA1).evaluate(unsigned);
        assertEquals(Outcome.NOT_VERIFIED, rsa.outcome());
        assertEquals("rsa_sha1_capability_undetermined", rsa.notVerifiedReason());
    }

    @Test
    void missingMetadataIsNotAProtocolViolation() {
        var outcome = new MetadataSignatureProfileCase(MetadataSignatureProfileCase.Rule.ENVELOPED).evaluate(null);
        assertEquals(Outcome.NOT_VERIFIED, outcome.outcome());
        assertEquals("target_metadata_unavailable", outcome.notVerifiedReason());
    }

    private com.samlscope.core.evaluation.CaseOutcome evaluate(
            MetadataSignatureProfileCase.Rule rule, byte[] metadata) {
        var result = new MetadataSignatureProfileCase(rule).evaluate(metadata);
        assertTrue(result.evidence().stream().anyMatch(value -> "target_metadata".equals(value.kind())));
        return result;
    }

    private byte[] signedMetadata(String id, String reference, String transforms) {
        return ("""
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                    xmlns:ds="http://www.w3.org/2000/09/xmldsig#" ID="%s">
                  <ds:Signature>
                    <ds:SignedInfo>
                      <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                      <ds:SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
                      <ds:Reference URI="%s"><ds:Transforms>%s</ds:Transforms></ds:Reference>
                    </ds:SignedInfo>
                    <ds:SignatureValue>AA==</ds:SignatureValue>
                  </ds:Signature>
                  <md:SPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol"/>
                </md:EntityDescriptor>
                """).formatted(id, reference, transforms).getBytes(StandardCharsets.UTF_8);
    }

    private String transforms(String... algorithms) {
        return java.util.Arrays.stream(algorithms)
                .map(value -> "<ds:Transform Algorithm=\"" + value + "\"/>")
                .collect(java.util.stream.Collectors.joining());
    }
}
