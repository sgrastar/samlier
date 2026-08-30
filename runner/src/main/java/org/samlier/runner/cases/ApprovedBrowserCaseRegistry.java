package org.samlier.runner.cases;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.samlier.core.casedef.CaseDefinitionCatalog;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.runner.CaseImplementationAudit;
import org.samlier.runner.TestCaseRegistry;

/** Builds the complete manual-user-agent M1 BROWSER slice from the signed G2 instructions. */
public final class ApprovedBrowserCaseRegistry {
    private static final Duration BROWSER_TTL = Duration.ofDays(7);
    private static final Duration EVIDENCE_TTL = Duration.ofDays(7);

    private ApprovedBrowserCaseRegistry() {}

    public static TestCaseRegistry create(CaseDefinitionCatalog definitions, URI publicBase) {
        return create(definitions, publicBase, Milestone.M1, null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions, URI publicBase, TranscriptContentReader transcriptContent) {
        return create(
                definitions, publicBase, Milestone.M1, transcriptContent,
                ignored -> java.util.Optional.empty(), ignored -> java.util.Optional.empty(), null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            URI publicBase,
            TranscriptContentReader transcriptContent,
            SamlDecryptionKeyProvider decryptionKeys) {
        return create(
                definitions, publicBase, Milestone.M1, transcriptContent, decryptionKeys,
                ignored -> java.util.Optional.empty(), null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            URI publicBase,
            TranscriptContentReader transcriptContent,
            SamlDecryptionKeyProvider decryptionKeys,
            java.util.function.Function<String, java.util.Optional<String>> targetEntityIds) {
        return create(
                definitions, publicBase, Milestone.M1, transcriptContent,
                decryptionKeys, targetEntityIds, null);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            URI publicBase,
            TranscriptContentReader transcriptContent,
            SamlDecryptionKeyProvider decryptionKeys,
            java.util.function.Function<String, java.util.Optional<String>> targetEntityIds,
            java.util.function.Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations) {
        return create(
                definitions, publicBase, Milestone.M1, transcriptContent,
                decryptionKeys, targetEntityIds, idpScenarioConfigurations);
    }

    public static TestCaseRegistry create(
            CaseDefinitionCatalog definitions, URI publicBase, Milestone milestone) {
        return create(definitions, publicBase, milestone, null);
    }

    private static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            URI publicBase,
            Milestone milestone,
            TranscriptContentReader transcriptContent) {
        return create(
                definitions, publicBase, milestone, transcriptContent,
                ignored -> java.util.Optional.empty(), ignored -> java.util.Optional.empty(), null);
    }

    private static TestCaseRegistry create(
            CaseDefinitionCatalog definitions,
            URI publicBase,
            Milestone milestone,
            TranscriptContentReader transcriptContent,
            SamlDecryptionKeyProvider decryptionKeys,
            java.util.function.Function<String, java.util.Optional<String>> targetEntityIds,
            java.util.function.Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(publicBase, "publicBase");
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(decryptionKeys, "decryptionKeys");
        var cases = new ArrayList<org.samlier.core.caseexec.TestCase>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == milestone)
                .filter(value -> value.mode() == ExecutionMode.BROWSER)
                .map(value -> createCase(
                        value, publicBase, transcriptContent, decryptionKeys, targetEntityIds,
                        idpScenarioConfigurations))
                .forEach(cases::add);
        var registry = new TestCaseRegistry(cases);
        CaseImplementationAudit.requireExact(definitions, registry, milestone, ExecutionMode.BROWSER);
        return registry;
    }

    private static org.samlier.core.caseexec.TestCase createCase(
            CaseDefinition definition,
            URI publicBase,
            TranscriptContentReader transcriptContent,
            SamlDecryptionKeyProvider decryptionKeys,
            java.util.function.Function<String, java.util.Optional<String>> targetEntityIds,
            java.util.function.Function<String, IdpErrorProbeConfiguration> idpScenarioConfigurations) {
        if (idpScenarioConfigurations != null && List.of(
                IdpNameIdPolicyScenarioTestCase.PROCESSING_CASE,
                IdpNameIdPolicyScenarioTestCase.REJECTION_CASE,
                IdpNameIdPolicyScenarioTestCase.CONFORMANCE_CASE).contains(definition.id())) {
            return new IdpNameIdPolicyScenarioTestCase(
                    definition.id(), idpScenarioConfigurations, decryptionKeys);
        }
        var transcriptDriven = transcriptContent != null && NormalFlowBrowserObservation.supports(definition.id());
        var evidence = new AttestedOutcomeTestCase(
                definition.id(), definition.role(), "case." + definition.id() + ".browser-evidence",
                evidencePrompt(definition), EVIDENCE_TTL,
                List.of(
                        AttestationOption.of(
                                "evidence_satisfies", Outcome.SATISFIED, "browser.evidence-satisfies"),
                        AttestationOption.of(
                                "evidence_violates", Outcome.VIOLATED, "browser.evidence-violates"),
                        AttestationOption.notVerified(
                                "unable_to_verify", "browser.evidence-unavailable",
                                "browser_evidence_unavailable")));
        var fallback = new BrowserEvidenceTestCase(
                evidence, publicBase, browserPrompt(definition, transcriptDriven), BROWSER_TTL);
        if (transcriptDriven) {
            return new AutoBrowserEvidenceTestCase(
                    fallback, transcriptContent, decryptionKeys, targetEntityIds);
        }
        return fallback;
    }

    private static String browserPrompt(CaseDefinition definition, boolean transcriptDriven) {
        var value = new StringBuilder()
                .append("Use a real browser as the SAML user agent for approved case ")
                .append(definition.id())
                .append(transcriptDriven
                        ? ". Complete every applicable target instruction. Samlier completes this case when the "
                                + "correlated Transcript becomes conclusive; do not submit a completion answer."
                        : ". Complete every applicable target instruction before marking the browser step complete.")
                .append("\n\nInstructions:\n");
        definition.variantPlan().forEach(item -> value.append("- ").append(item.instructionEn()).append('\n'));
        // G2 controls prove the Suite's oracle against baseline and mutant fixtures. They are not
        // additional actions to perform against the real target represented by this Run.
        return value.toString();
    }

    private static String evidencePrompt(CaseDefinition definition) {
        var value = new StringBuilder()
                .append("Review the evidence produced by the completed browser steps for ")
                .append(definition.obligation())
                .append(". Select a conclusive result only when the observed browser and Transcript evidence supports it; ")
                .append("otherwise select unable_to_verify.");
        if (!definition.interpretationConstraints().isEmpty()) {
            value.append("\n\nInterpretation constraints:\n");
            definition.interpretationConstraints().forEach(item -> value.append("- ").append(item).append('\n'));
        }
        value.append("\nCounterexample to avoid:\n").append(definition.counterexampleEn());
        return value.toString();
    }
}
