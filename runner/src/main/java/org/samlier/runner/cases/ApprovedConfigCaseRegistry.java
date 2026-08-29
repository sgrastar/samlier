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

/** Builds the M1 CONFIG slice as a configuration gate followed by explicit evidence review. */
public final class ApprovedConfigCaseRegistry {
    private static final Duration CONFIG_TTL = Duration.ofDays(7);
    private static final Duration EVIDENCE_TTL = Duration.ofDays(7);

    private ApprovedConfigCaseRegistry() {}

    public static TestCaseRegistry create(CaseDefinitionCatalog definitions) {
        Objects.requireNonNull(definitions, "definitions");
        var cases = new ArrayList<ConfigurationGateTestCase>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == Milestone.M1)
                .filter(value -> value.mode() == ExecutionMode.CONFIG)
                .map(ApprovedConfigCaseRegistry::createCase)
                .forEach(cases::add);
        var registry = new TestCaseRegistry(cases);
        CaseImplementationAudit.requireExact(definitions, registry, Milestone.M1, ExecutionMode.CONFIG);
        return registry;
    }

    private static ConfigurationGateTestCase createCase(CaseDefinition definition) {
        var evidence = new AttestedOutcomeTestCase(
                definition.id(), definition.role(), "case." + definition.id() + ".evidence",
                evidencePrompt(definition), EVIDENCE_TTL,
                List.of(
                        AttestationOption.of(
                                "evidence_satisfies", Outcome.SATISFIED, "configuration.evidence-satisfies"),
                        AttestationOption.of(
                                "evidence_violates", Outcome.VIOLATED, "configuration.evidence-violates"),
                        AttestationOption.notVerified(
                                "unable_to_verify", "configuration.evidence-unavailable",
                                "configuration_evidence_unavailable")));
        return new ConfigurationGateTestCase(
                evidence,
                "case." + definition.id() + ".configuration",
                configurationPrompt(definition),
                CONFIG_TTL,
                definition.configurationFailureSemantics());
    }

    private static String configurationPrompt(CaseDefinition definition) {
        return "Prepare the target configuration required by the approved case " + definition.id()
                + ". Confirm only after the configuration is active. If the capability is absent, unavailable, or "
                + "undetermined, choose the matching answer so the common configuration semantics can be applied.";
    }

    private static String evidencePrompt(CaseDefinition definition) {
        var value = new StringBuilder()
                .append("Execute the approved CONFIG evidence plan for ")
                .append(definition.obligation())
                .append(" after the target configuration has been confirmed. Select a conclusive result only when ")
                .append("the observed evidence supports it; otherwise select unable_to_verify.\n\nEvidence instructions:\n");
        definition.variantPlan().forEach(item -> value.append("- ").append(item.instructionEn()).append('\n'));
        value.append("\nRequired controls:\n");
        definition.controls().forEach(item -> value.append("- ").append(item.descriptionEn()).append('\n'));
        if (!definition.interpretationConstraints().isEmpty()) {
            value.append("\nInterpretation constraints:\n");
            definition.interpretationConstraints().forEach(item -> value.append("- ").append(item).append('\n'));
        }
        value.append("\nCounterexample to avoid:\n").append(definition.counterexampleEn());
        return value.toString();
    }
}
