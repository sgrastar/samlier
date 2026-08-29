package org.samlier.runner.cases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(milestone, "milestone");
        var cases = new ArrayList<AttestedOutcomeTestCase>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == milestone)
                .filter(value -> value.mode() == ExecutionMode.ATTESTED)
                .map(ApprovedAttestedCaseRegistry::createCase)
                .forEach(cases::add);
        var registry = new TestCaseRegistry(cases);
        CaseImplementationAudit.requireExact(definitions, registry, milestone, ExecutionMode.ATTESTED);
        return registry;
    }

    private static AttestedOutcomeTestCase createCase(CaseDefinition definition) {
        return new AttestedOutcomeTestCase(
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
