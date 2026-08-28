package org.samlier.saml.metadata;

import java.net.URI;
import java.util.List;

public record TargetMetadata(
        String entityId,
        List<Endpoint> singleSignOnServices,
        List<Endpoint> assertionConsumerServices) {
    public TargetMetadata {
        singleSignOnServices = List.copyOf(singleSignOnServices);
        assertionConsumerServices = List.copyOf(assertionConsumerServices);
    }

    public record Endpoint(String binding, URI location, Integer index, boolean isDefault) {}
}
