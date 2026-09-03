package com.samlscope.saml.metadata;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;

public record TargetMetadata(
        String entityId,
        List<Endpoint> singleSignOnServices,
        List<Endpoint> singleLogoutServices,
        List<Endpoint> assertionConsumerServices,
        List<X509Certificate> signingCertificates) {
    public TargetMetadata {
        singleSignOnServices = List.copyOf(singleSignOnServices);
        singleLogoutServices = List.copyOf(singleLogoutServices);
        assertionConsumerServices = List.copyOf(assertionConsumerServices);
        signingCertificates = List.copyOf(signingCertificates);
    }

    public record Endpoint(String binding, URI location, Integer index, boolean isDefault) {}
}
