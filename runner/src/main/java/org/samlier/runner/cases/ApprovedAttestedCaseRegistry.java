package org.samlier.runner.cases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.casedef.CaseDefinitionCatalog;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.evaluation.Outcome;
import org.samlier.runner.CaseImplementationAudit;
import org.samlier.runner.TestCaseRegistry;

/** Builds the complete M1 ATTESTED registry directly from the signed G2 case designs. */
public final class ApprovedAttestedCaseRegistry {
    private static final Duration ATTESTATION_TTL = Duration.ofDays(7);

    private ApprovedAttestedCaseRegistry() {}

    public static TestCaseRegistry create(CaseDefinitionCatalog definitions) {
        return create(definitions, Milestone.M1);
    }

    public static TestCaseRegistry create(CaseDefinitionCatalog definitions, Milestone milestone) {
        return create(definitions, milestone, null, null, null, null, null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            Milestone milestone,
            java.net.URI publicBase,
            Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations,
            org.samlier.core.transcript.TranscriptContentReader transcriptContent,
            Function<String, java.util.Optional<String>> targetEntityIds,
            Function<String, List<java.security.cert.X509Certificate>> targetSigningCertificates) {
        return create(
                definitions, milestone, publicBase, idpScenarioConfigurations, transcriptContent,
                targetEntityIds, targetSigningCertificates, null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            Milestone milestone,
            java.net.URI publicBase,
            Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations,
            org.samlier.core.transcript.TranscriptContentReader transcriptContent,
            Function<String, java.util.Optional<String>> targetEntityIds,
            Function<String, List<java.security.cert.X509Certificate>> targetSigningCertificates,
            Function<String, byte[]> targetMetadata) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(milestone, "milestone");
        var cases = new ArrayList<org.samlier.core.caseexec.TestCase>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == milestone)
                .filter(value -> value.mode() == ExecutionMode.ATTESTED)
                .map(value -> createCase(
                        value, idpScenarioConfigurations, transcriptContent,
                        targetEntityIds, targetSigningCertificates, publicBase, targetMetadata))
                .forEach(cases::add);
        var registry = new TestCaseRegistry(cases);
        CaseImplementationAudit.requireExact(definitions, registry, milestone, ExecutionMode.ATTESTED);
        return registry;
    }

    private static org.samlier.core.caseexec.TestCase createCase(
            CaseDefinition definition,
            Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations,
            org.samlier.core.transcript.TranscriptContentReader transcriptContent,
            Function<String, java.util.Optional<String>> targetEntityIds,
            Function<String, List<java.security.cert.X509Certificate>> targetSigningCertificates,
            java.net.URI publicBase,
            Function<String, byte[]> targetMetadata) {
        if (List.of("IIP-IDP13-b-idp-01", "IIP-IDP14-b-idp-01").contains(definition.id())) {
            return new InformationalChoiceTestCase(definition.id(), definition.role());
        }
        if (idpScenarioConfigurations != null
                && IdpForceAuthnScenarioTestCase.MECHANISM_ACCESS_CASE.equals(definition.id())) {
            return new IdpForceAuthnScenarioTestCase(definition.id(), idpScenarioConfigurations);
        }
        if (idpScenarioConfigurations != null
                && IdpTimePrecisionScenarioTestCase.CASE_ID.equals(definition.id())) {
            return new IdpTimePrecisionScenarioTestCase(idpScenarioConfigurations);
        }
        if (transcriptContent != null && publicBase != null
                && "IIP-IDP17-ak-idp-01".equals(definition.id())) {
            return new LogoutAttestedEvidenceTestCase(
                    definition.id(), definition.role(), publicBase, transcriptContent,
                    targetEntityIds == null ? ignored -> java.util.Optional.empty() : targetEntityIds,
                    targetSigningCertificates == null ? ignored -> List.of() : targetSigningCertificates,
                    LogoutTranscriptProfileCase.Rule.REQUEST_VERSION_2);
        }
        var fallback = new AttestedOutcomeTestCase(
                definition.id(),
                definition.role(),
                "case." + definition.id() + ".attestation",
                prompt(definition),
                ATTESTATION_TTL,
                List.of(
                        AttestationOption.of(
                                "satisfied", Outcome.SATISFIED, "attestation.satisfied"),
                        AttestationOption.of(
                                "violated", Outcome.VIOLATED, "attestation.violated"),
                        AttestationOption.notVerified(
                                "unable_to_verify", "attestation.unavailable", "attestation_unavailable")));
        if (targetMetadata != null && List.of("IIP-MD09-a-idp-01", "IIP-MD09-a-sp-01")
                .contains(definition.id())) {
            return new AutoAttestedMetadataEvidenceTestCase(fallback, targetMetadata);
        }
        return fallback;
    }

    private static String prompt(CaseDefinition definition) {
        var value = new StringBuilder()
                .append("Review the approved evidence instructions for ")
                .append(definition.obligation())
                .append(". Select satisfied or violated only when the available evidence supports that result; ")
                .append("otherwise select unable_to_verify.\n\nEvidence instructions:\n");
        for (var variant : definition.variantPlan()) {
            value.append("- ").append(variant.instructionEn()).append('\n');
        }
        if (!definition.interpretationConstraints().isEmpty()) {
            value.append("\nInterpretation constraints:\n");
            definition.interpretationConstraints().forEach(item -> value.append("- ").append(item).append('\n'));
        }
        value.append("\nCounterexample to avoid:\n").append(definition.counterexampleEn());
        return value.toString();
    }
}
