package org.samlier.runner.result;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.samlier.core.evaluation.ApplicabilityEvaluation.Basis;
import org.samlier.core.evaluation.CaseRun;
import org.samlier.core.evaluation.CoverageCatalog;
import org.samlier.core.evaluation.Evaluator;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.evaluation.RunResult;
import org.samlier.core.evaluation.RunResultInvariantValidator;
import org.samlier.core.evaluation.Verdict;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;
import org.samlier.runner.result.ResultDocument.AdvisoryView;
import org.samlier.runner.result.ResultDocument.ApplicabilityView;
import org.samlier.runner.result.ResultDocument.CaseView;
import org.samlier.runner.result.ResultDocument.CountView;
import org.samlier.runner.result.ResultDocument.EvidenceView;
import org.samlier.runner.result.ResultDocument.ObligationView;
import org.samlier.runner.result.ResultDocument.RequirementView;

/** Builds schema-v1 public results without re-determining conformance. */
public final class ResultDocumentAssembler {
    private ResultDocumentAssembler() {}

    public static ResultDocument assemble(
            CoverageCatalog catalog,
            TestPlan plan,
            TestRun run,
            RunResult evaluation,
            List<CaseRun> caseRuns,
            ResultDocumentContext context) {
        java.util.Objects.requireNonNull(catalog, "catalog");
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(run, "run");
        java.util.Objects.requireNonNull(evaluation, "evaluation");
        caseRuns = List.copyOf(caseRuns == null ? List.of() : caseRuns);
        java.util.Objects.requireNonNull(context, "context");
        if (!run.planId().equals(plan.id())) throw new IllegalArgumentException("Run does not belong to plan");
        RunResultInvariantValidator.validate(catalog, plan, evaluation);

        var sourceByKey = catalog.byKey();
        var resultByKey = new LinkedHashMap<String, RunResult.ObligationResult>();
        for (var value : evaluation.obligations()) resultByKey.put(value.key(), value);
        var caseById = casesById(caseRuns, resultByKey);
        validateCaseReferences(evaluation, caseById);

        var requirements = new ArrayList<RequirementView>();
        for (var requirement : evaluation.requirements()) {
            var specUrl = required(context.requirementSpecUrls(), requirement.id(), "requirement spec URL");
            var obligations = new ArrayList<ObligationView>();
            var cases = new ArrayList<CaseView>();
            for (var key : requirement.obligationKeys()) {
                var source = sourceByKey.get(key);
                var result = resultByKey.get(key);
                obligations.add(new ObligationView(key, result.level(), plan.profile().role(), result.verdict()));
                for (var caseId : result.caseIds()) {
                    cases.add(caseView(
                            caseById.get(caseId), source, context.caseDefinitionUrls()));
                }
            }
            requirements.add(new RequirementView(
                    requirement.id(), requirement.verdict(), specUrl, obligations, cases));
        }

        var applicability = evaluation.applicability().stream().map(value -> new ApplicabilityView(
                value.obligationKey(), value.predicate(), value.predicateKind(), value.declared(), value.observed(),
                value.effectiveResult(), value.conflict(), value.basis(),
                value.evidenceRefs().stream().map(ref -> new EvidenceView("applicability", ref)).toList())).toList();
        var qualifications = evaluation.scopeQualifications().stream().map(value ->
                new ResultDocument.ScopeQualificationView(
                        value.kind(), value.predicate(), value.excludedObligations(), value.reason(),
                        value.attestedBy(), value.attestedAt(), value.verified())).toList();

        var unresolved = evaluation.obligations().stream()
                .filter(value -> unresolved(value.verdict()))
                .map(value -> new ResultDocument.UnresolvedView(
                        value.key(), value.level(), value.verdict(), value.reasons(), resolution(value.reasons())))
                .toList();
        var notObservable = evaluation.obligations().stream()
                .filter(value -> value.verdict() == Verdict.NOT_OBSERVABLE)
                .map(value -> new ResultDocument.NotObservableView(
                        value.key(), value.level(), "The obligation cannot be verified from the external protocol surface."))
                .toList();
        var components = context.evaluationComponents();
        var coverage = evaluation.coverage();

        return new ResultDocument(
                "1",
                new ResultDocument.RunView(
                        run.id(), run.createdAt(), run.updatedAt(), evaluation.conformance(), evaluation.completeness(),
                        qualifications),
                new ResultDocument.SuiteView(
                        context.suite().name(), context.suite().version(), context.suite().imageDigest(),
                        context.suite().executionMode()),
                new ResultDocument.EvaluationBundleView(
                        components.compositeDigest(),
                        new ResultDocument.EvaluationComponentsView(
                                components.coverageYaml(), components.testDefinitions(), components.specsYaml(),
                                components.outcomeMappingVersion(), components.aggregationPolicyVersion())),
                new ResultDocument.ProfileView(
                        profileId(plan),
                        new ResultDocument.SpecView(
                                context.profileSpec().document(), context.profileSpec().version(),
                                context.profileSpec().date()),
                        context.profileSpec().levelDefinitionNote()),
                new ResultDocument.TargetView(
                        context.target().product(), context.target().declaredBy(), false,
                        plan.target().entityId(), context.target().metadataDigest(), plan.profile().role(),
                        plan.target().kind()),
                new ResultDocument.ConfigurationView(
                        plan.suiteMetadataDelivery(), run.targetToSuiteReachability(), plan.declaredFeatures(),
                        new ResultDocument.ParametersView(
                                plan.parameters().clockSkewToleranceSeconds(),
                                plan.parameters().metadataRefreshWaitSeconds())),
                applicability,
                context.advisories(),
                evaluation.suiteIncidents().stream().map(value -> new ResultDocument.SuiteIncidentView(
                        value.kind(), value.caseId(), value.actionId(), value.note())).toList(),
                new ResultDocument.SummaryView(
                        countRequirements(evaluation), countObligations(evaluation), countCases(caseRuns, sourceByKey)),
                new ResultDocument.CoverageView(
                        coverage.obligationsTotal(), coverage.obligationsApplicable(), coverage.mustApplicable(),
                        coverage.mustObservable(), coverage.mustResolved(), coverage.mustUnresolved(),
                        coverage.mustNotObservable(), coverage.attestedObligations(),
                        coverage.applicabilityFromDeclarationOnly(), coverage.excludedByDeclaration(),
                        coverage.verifiedRatio()),
                requirements,
                unresolved,
                notObservable,
                conformanceStatement(evaluation, notObservable));
    }

    private static Map<String, CaseRun> casesById(
            List<CaseRun> cases,
            Map<String, RunResult.ObligationResult> obligations) {
        var result = new LinkedHashMap<String, CaseRun>();
        for (var value : cases) {
            if (!obligations.containsKey(value.obligationKey())) {
                throw new IllegalArgumentException("Case is outside the selected result: " + value.id());
            }
            if (result.put(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate CaseRun: " + value.id());
            }
        }
        return result;
    }

    private static void validateCaseReferences(RunResult evaluation, Map<String, CaseRun> caseById) {
        var expected = new java.util.LinkedHashSet<String>();
        for (var obligation : evaluation.obligations()) {
            for (var id : obligation.caseIds()) {
                if (!expected.add(id)) throw new IllegalStateException("Case is attached to multiple obligations: " + id);
                var run = caseById.get(id);
                if (run == null || !run.obligationKey().equals(obligation.key())) {
                    throw new IllegalArgumentException("CaseRun does not match Evaluator output: " + id);
                }
            }
        }
        if (!expected.equals(caseById.keySet())) {
            throw new IllegalArgumentException("CaseRun set does not match Evaluator output");
        }
    }

    private static CaseView caseView(
            CaseRun value,
            CoverageCatalog.Obligation source,
            Map<String, String> definitionUrls) {
        var outcome = value.outcome();
        var verdict = outcome == null ? Verdict.ERROR : Evaluator.toVerdict(source.level(), outcome);
        var reasonCode = outcome == null ? "suite_error"
                : outcome.reasonCode() == null ? outcome.outcome().name().toLowerCase(Locale.ROOT) : outcome.reasonCode();
        var reason = outcome == null ? value.suiteErrorReason()
                : outcome.reasonMessageKey() == null ? reasonCode : outcome.reasonMessageKey();
        return new CaseView(
                value.id(), value.obligationKey(), outcome == null ? null : outcome.outcome(), verdict,
                source.testability().name(), reasonCode, reason,
                source.testability() == CoverageCatalog.Testability.ATTESTED,
                outcome == null ? List.of() : outcome.evidence().stream()
                        .map(ref -> new EvidenceView(ref.kind(), ref.reference())).toList(),
                required(definitionUrls, value.id(), "case definition URL"));
    }

    private static CountView countRequirements(RunResult result) {
        var counts = verdictCounts();
        for (var value : result.requirements()) counts.merge(value.verdict(), 1, Integer::sum);
        return new CountView(result.requirements().size(), names(counts));
    }

    private static CountView countObligations(RunResult result) {
        return new CountView(result.obligations().size(), names(result.coverage().verdictCounts()));
    }

    private static CountView countCases(
            List<CaseRun> cases,
            Map<String, CoverageCatalog.Obligation> obligations) {
        var counts = verdictCounts();
        for (var value : cases) {
            var verdict = value.outcome() == null
                    ? Verdict.ERROR
                    : Evaluator.toVerdict(obligations.get(value.obligationKey()).level(), value.outcome());
            counts.merge(verdict, 1, Integer::sum);
        }
        return new CountView(cases.size(), names(counts));
    }

    private static EnumMap<Verdict, Integer> verdictCounts() {
        var result = new EnumMap<Verdict, Integer>(Verdict.class);
        for (var verdict : Verdict.values()) result.put(verdict, 0);
        return result;
    }

    private static Map<String, Integer> names(Map<Verdict, Integer> values) {
        var result = new LinkedHashMap<String, Integer>();
        for (var verdict : Verdict.values()) {
            result.put(verdict.name().toLowerCase(Locale.ROOT), values.getOrDefault(verdict, 0));
        }
        return Map.copyOf(result);
    }

    private static String profileId(TestPlan plan) {
        return plan.profile().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean unresolved(Verdict verdict) {
        return switch (verdict) {
            case NOT_VERIFIED, INDETERMINATE, INCONSISTENT, ERROR -> true;
            default -> false;
        };
    }

    private static String resolution(List<String> reasons) {
        if (reasons.contains("applicability_undetermined")) {
            return "Provide or collect the missing applicability declaration or observation, then rerun.";
        }
        if (reasons.contains("not_implemented")) {
            return "Use a Suite release that implements this case, then rerun.";
        }
        if (reasons.contains("applicability_conflict")) {
            return "Resolve the conflict between the declaration and observed evidence, then rerun.";
        }
        return "Resolve the recorded reason and rerun the obligation.";
    }

    private static String conformanceStatement(
            RunResult evaluation,
            List<ResultDocument.NotObservableView> notObservable) {
        var coverage = evaluation.coverage();
        var statement = new StringBuilder()
                .append("Conformance: ").append(evaluation.conformance())
                .append("; completeness: ").append(evaluation.completeness()).append(". Resolved ")
                .append(coverage.mustResolved()).append(" of ").append(coverage.mustObservable())
                .append(" externally testable MUST-class obligations.");
        if (!notObservable.isEmpty()) {
            statement.append(" ").append(notObservable.size())
                    .append(" MUST-class obligation(s) were not externally observable: ")
                    .append(String.join(", ", notObservable.stream()
                            .map(ResultDocument.NotObservableView::obligation).toList()))
                    .append(".");
        }
        if (coverage.excludedByDeclaration() > 0) {
            statement.append(" ").append(coverage.excludedByDeclaration())
                    .append(" obligation(s) were excluded using unverified declarations; see scope_qualifications.");
        }
        return statement.append(" This is a test result, not a certification.").toString();
    }

    private static String required(Map<String, String> values, String key, String name) {
        var value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name + ": " + key);
        return value;
    }
}
