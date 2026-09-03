package com.samlscope.runner.cases;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Passive checks for the optional IdP Discovery metadata extension. */
public final class DiscoveryMetadataProfileCase {
    public enum Rule { FIXED_BINDING, INDEXED_ENDPOINT_STRUCTURE }
    private static final String DISCOVERY = "urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol";
    private static final String BINDING = DISCOVERY;
    private final Rule rule;

    public DiscoveryMetadataProfileCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(byte[] metadata) {
        if (metadata == null || metadata.length == 0) {
            return CaseOutcome.notVerified("target_metadata_unavailable", "discovery.metadata-unavailable");
        }
        var document = SecureXml.parse(metadata);
        var values = document.getElementsByTagNameNS(DISCOVERY, "DiscoveryResponse");
        var evidence = List.of(new EvidenceRef("target_metadata", "sha256:" + sha256(metadata)));
        if (values.getLength() == 0) {
            return new CaseOutcome(
                    Outcome.SATISFIED_WITH_NOTE, null, "discovery.extension.not-published",
                    "discovery.extension.not-published", evidence, Map.of("observed", 0));
        }
        var violations = new ArrayList<String>();
        for (var index = 0; index < values.getLength(); index++) {
            var endpoint = (Element) values.item(index);
            if (rule == Rule.FIXED_BINDING) {
                if (!BINDING.equals(endpoint.getAttribute("Binding"))) {
                    violations.add("DiscoveryResponse[" + index + "]:wrong-binding");
                }
            } else if (endpoint.getAttribute("Location").isBlank()
                    || endpoint.getAttribute("index").isBlank()
                    || endpoint.getAttribute("Binding").isBlank()
                    || !integer(endpoint.getAttribute("index"))) {
                violations.add("DiscoveryResponse[" + index + "]:invalid-indexed-endpoint");
            }
        }
        return new CaseOutcome(
                violations.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED, null,
                violations.isEmpty() ? "discovery.metadata.satisfied" : "discovery.metadata.violated",
                violations.isEmpty() ? "discovery.metadata.satisfied" : "discovery.metadata.violated",
                evidence, Map.of("observed", values.getLength(), "violations", List.copyOf(violations)));
    }

    private boolean integer(String value) {
        try { return Integer.parseUnsignedInt(value) >= 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
