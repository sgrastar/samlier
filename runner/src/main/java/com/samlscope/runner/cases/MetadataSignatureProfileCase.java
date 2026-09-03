package com.samlscope.runner.cases;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Passive syntax/profile checks over every signature in target-issued metadata. */
public final class MetadataSignatureProfileCase {
    public enum Rule { ENVELOPED, SIGNED_ELEMENT_ID, SINGLE_ROOT_REFERENCE, EXCLUSIVE_C14N, ALLOWED_TRANSFORMS, RSA_SHA1 }

    private static final String MD = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String ENVELOPED = DS + "enveloped-signature";
    private static final String EXCLUSIVE = "http://www.w3.org/2001/10/xml-exc-c14n#";
    private static final String EXCLUSIVE_COMMENTS = EXCLUSIVE + "WithComments";
    private static final String RSA_SHA1 = DS + "rsa-sha1";
    private static final Set<String> ALLOWED_TRANSFORMS = Set.of(ENVELOPED, EXCLUSIVE, EXCLUSIVE_COMMENTS);
    private final Rule rule;

    public MetadataSignatureProfileCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(byte[] metadata) {
        if (metadata == null || metadata.length == 0) {
            return CaseOutcome.notVerified("target_metadata_unavailable", "metadata.signature.no-metadata");
        }
        var document = SecureXml.parse(metadata);
        var signatures = document.getElementsByTagNameNS(DS, "Signature");
        var evidence = List.of(new EvidenceRef("target_metadata", "sha256:" + digest(metadata)));
        if (signatures.getLength() == 0) {
            return rule == Rule.RSA_SHA1
                    ? CaseOutcome.notVerified("rsa_sha1_capability_undetermined", "metadata.rsa-sha1.unobserved")
                    : new CaseOutcome(
                            Outcome.SATISFIED_WITH_NOTE, null, "metadata.signature.not-observed",
                            "metadata.signature.not-observed", evidence, Map.of("inspected_signatures", 0));
        }
        var violations = new ArrayList<String>();
        var rsaSha1Observed = false;
        for (var index = 0; index < signatures.getLength(); index++) {
            var signature = (Element) signatures.item(index);
            var reason = violation(signature);
            if (reason != null) violations.add(reason);
            if (algorithms(signature, "SignatureMethod").contains(RSA_SHA1)) rsaSha1Observed = true;
        }
        if (rule == Rule.RSA_SHA1) {
            return rsaSha1Observed
                    ? new CaseOutcome(
                            Outcome.SATISFIED, null, "metadata.rsa-sha1.observed",
                            "metadata.rsa-sha1.observed", evidence,
                            Map.of("inspected_signatures", signatures.getLength()))
                    : CaseOutcome.notVerified(
                            "rsa_sha1_capability_undetermined", "metadata.rsa-sha1.capability-undetermined");
        }
        return new CaseOutcome(
                violations.isEmpty() ? Outcome.SATISFIED : Outcome.VIOLATED,
                null,
                violations.isEmpty() ? "metadata.signature.profile-satisfied" : "metadata.signature.profile-violated",
                violations.isEmpty() ? "metadata.signature.profile-satisfied" : "metadata.signature.profile-violated",
                evidence,
                Map.of("inspected_signatures", signatures.getLength(), "violations", List.copyOf(violations)));
    }

    private String violation(Element signature) {
        var parent = signature.getParentNode();
        if (!(parent instanceof Element signed) || !MD.equals(signed.getNamespaceURI())) {
            return "signature-not-enveloped-by-metadata-element";
        }
        return switch (rule) {
            case ENVELOPED -> algorithms(signature, "Transform").contains(ENVELOPED)
                    ? null : "enveloped-signature-transform-missing";
            case SIGNED_ELEMENT_ID -> signed.getAttribute("ID").isBlank() ? "signed-element-id-missing" : null;
            case SINGLE_ROOT_REFERENCE -> referenceViolation(signature, signed);
            case EXCLUSIVE_C14N -> exclusiveViolation(signature);
            case ALLOWED_TRANSFORMS -> algorithms(signature, "Transform").stream().allMatch(ALLOWED_TRANSFORMS::contains)
                    ? null : "unauthorized-transform-present";
            case RSA_SHA1 -> null;
        };
    }

    private String referenceViolation(Element signature, Element signed) {
        if (signed.getAttribute("ID").isBlank()) return "signed-element-id-missing";
        var references = signature.getElementsByTagNameNS(DS, "Reference");
        if (references.getLength() != 1) return "reference-count-" + references.getLength();
        var uri = ((Element) references.item(0)).getAttribute("URI");
        if (!("#" + signed.getAttribute("ID")).equals(uri)) return "reference-does-not-match-signed-element";
        return algorithms((Element) references.item(0), "Transform").stream()
                .allMatch(ALLOWED_TRANSFORMS::contains) ? null : "content-coverage-not-established";
    }

    private String exclusiveViolation(Element signature) {
        var canonicalization = algorithms(signature, "CanonicalizationMethod");
        if (canonicalization.size() != 1 || !exclusive(canonicalization.getFirst())) {
            return "signed-info-canonicalization-not-exclusive";
        }
        var references = signature.getElementsByTagNameNS(DS, "Reference");
        for (var index = 0; index < references.getLength(); index++) {
            if (algorithms((Element) references.item(index), "Transform").stream().noneMatch(this::exclusive)) {
                return "reference-canonicalization-not-exclusive";
            }
        }
        return null;
    }

    private List<String> algorithms(Element parent, String localName) {
        var result = new ArrayList<String>();
        var values = parent.getElementsByTagNameNS(DS, localName);
        for (var index = 0; index < values.getLength(); index++) {
            result.add(((Element) values.item(index)).getAttribute("Algorithm"));
        }
        return result;
    }

    private boolean exclusive(String value) {
        return EXCLUSIVE.equals(value) || EXCLUSIVE_COMMENTS.equals(value);
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
