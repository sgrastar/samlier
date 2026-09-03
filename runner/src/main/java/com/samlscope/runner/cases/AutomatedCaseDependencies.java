package com.samlscope.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import com.samlscope.core.caseexec.CaseExecutionRepository;
import com.samlscope.core.transcript.TranscriptContentReader;

/** Runtime evidence and fixtures required to instantiate every approved M1 automated case. */
public record AutomatedCaseDependencies(
        TranscriptContentReader transcriptContent,
        Map<String, SamlAttributeReleaseFixture> attributeFixtures,
        Map<String, SamlOptionalFieldObservationCase.Selector> optionalFieldSelectors,
        List<X509Certificate> targetSigningCertificates,
        String peerEntityId,
        SamlDecryptionKeyProvider decryptionKeys,
        PrincipalIdentityResolver principalIdentities,
        CaseExecutionRepository caseExecutions,
        IdpErrorProbeConfiguration idpErrorProbe) {

    public AutomatedCaseDependencies {
        java.util.Objects.requireNonNull(transcriptContent, "transcriptContent");
        attributeFixtures = Map.copyOf(attributeFixtures == null ? Map.of() : attributeFixtures);
        optionalFieldSelectors = Map.copyOf(optionalFieldSelectors == null ? Map.of() : optionalFieldSelectors);
        targetSigningCertificates = List.copyOf(
                targetSigningCertificates == null ? List.of() : targetSigningCertificates);
        if (peerEntityId == null || peerEntityId.isBlank()) throw new IllegalArgumentException("peerEntityId must not be blank");
        java.util.Objects.requireNonNull(decryptionKeys, "decryptionKeys");
        java.util.Objects.requireNonNull(principalIdentities, "principalIdentities");
        java.util.Objects.requireNonNull(caseExecutions, "caseExecutions");
        java.util.Objects.requireNonNull(idpErrorProbe, "idpErrorProbe");
    }

    SamlAttributeReleaseFixture attributeFixture(String id) {
        var fixture = attributeFixtures.get(id);
        if (fixture == null) throw new IllegalArgumentException("Missing attribute fixture for " + id);
        return fixture;
    }

    SamlOptionalFieldObservationCase.Selector optionalSelector(String id) {
        var selector = optionalFieldSelectors.get(id);
        if (selector == null) throw new IllegalArgumentException("Missing optional-field selector for " + id);
        return selector;
    }
}
