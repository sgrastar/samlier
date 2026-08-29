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
import org.samlier.runner.CaseImplementationAudit;
import org.samlier.runner.TestCaseRegistry;

/** Builds the complete manual-user-agent M1 BROWSER slice from the signed G2 instructions. */
public final class ApprovedBrowserCaseRegistry {
    private static final Duration BROWSER_TTL = Duration.ofDays(7);
    private static final Duration EVIDENCE_TTL = Duration.ofDays(7);

    private ApprovedBrowserCaseRegistry() {}

    public static TestCaseRegistry create(CaseDefinitionCatalog definitions, URI publicBase) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(publicBase, "publicBase");
        var cases = new ArrayList<BrowserEvidenceTestCase>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == Milestone.M1)
                .filter(value -> value.mode() == ExecutionMode.BROWSER)
                .map(value -> createCase(value, publicBase))
                .forEach(cases::add);
        var registry = new TestCaseRegistry(cases);
        CaseImplementationAudit.requireExact(definitions, registry, Milestone.M1, ExecutionMode.BROWSER);
        return registry;
    }

    private static BrowserEvidenceTestCase createCase(CaseDefinition definition, URI publicBase) {
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
        return new BrowserEvidenceTestCase(
                evidence, publicBase, browserPrompt(definition), BROWSER_TTL);
    }

    private static String browserPrompt(CaseDefinition definition) {
        var value = new StringBuilder()
                .append("Use a real browser as the SAML user agent for approved case ")
                .append(definition.id())
                .append(". Complete every applicable instruction and required control before marking the browser step complete.\n\nInstructions:\n");
        definition.variantPlan().forEach(item -> value.append("- ").append(item.instructionEn()).append('\n'));
        value.append("\nRequired controls:\n");
        definition.controls().forEach(item -> value.append("- ").append(item.descriptionEn()).append('\n'));
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
