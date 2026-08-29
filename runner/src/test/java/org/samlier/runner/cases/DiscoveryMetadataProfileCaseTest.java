package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class DiscoveryMetadataProfileCaseTest {
    @Test
    void optionalAbsenceIsNotAReceiverOrProducerViolation() {
        var xml = "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\"/>"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(Outcome.SATISFIED_WITH_NOTE,
                new DiscoveryMetadataProfileCase(DiscoveryMetadataProfileCase.Rule.FIXED_BINDING)
                        .evaluate(xml).outcome());
    }

    @Test
    void checksBindingAndIndexedEndpointStructureSeparately() {
        var good = metadata("urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol", "0", "https://sp.example/discovery");
        assertEquals(Outcome.SATISFIED,
                new DiscoveryMetadataProfileCase(DiscoveryMetadataProfileCase.Rule.FIXED_BINDING).evaluate(good).outcome());
        assertEquals(Outcome.SATISFIED,
                new DiscoveryMetadataProfileCase(DiscoveryMetadataProfileCase.Rule.INDEXED_ENDPOINT_STRUCTURE).evaluate(good).outcome());
        assertEquals(Outcome.VIOLATED,
                new DiscoveryMetadataProfileCase(DiscoveryMetadataProfileCase.Rule.FIXED_BINDING)
                        .evaluate(metadata("wrong", "0", "https://sp.example/discovery")).outcome());
        assertEquals(Outcome.VIOLATED,
                new DiscoveryMetadataProfileCase(DiscoveryMetadataProfileCase.Rule.INDEXED_ENDPOINT_STRUCTURE)
                        .evaluate(metadata("urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol", "x", "")).outcome());
    }

    private byte[] metadata(String binding, String index, String location) {
        return ("""
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                    xmlns:idpdisc="urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol">
                  <md:SPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:Extensions><idpdisc:DiscoveryResponse Binding="%s" index="%s" Location="%s"/></md:Extensions>
                  </md:SPSSODescriptor>
                </md:EntityDescriptor>
                """).formatted(binding, index, location).getBytes(StandardCharsets.UTF_8);
    }
}
