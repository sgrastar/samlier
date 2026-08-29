package org.samlier.core.evaluation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.samlier.core.evaluation.ApplicabilityEvaluation.Basis;
import org.samlier.core.evaluation.ApplicabilityEvaluation.EffectiveResult;
import org.samlier.core.evaluation.CoverageCatalog.Obligation;
import org.samlier.core.evaluation.CoverageCatalog.Testability;
import org.samlier.core.evaluation.Rfc2119Level.LevelClass;
import org.samlier.core.evaluation.RunResult.Completeness;
import org.samlier.core.evaluation.RunResult.Conformance;
import org.samlier.core.evaluation.RunResult.CoverageMetrics;
import org.samlier.core.evaluation.RunResult.ObligationResult;
import org.samlier.core.evaluation.RunResult.RequirementResult;
import org.samlier.core.evaluation.RunResult.ScopeQualification;
import org.samlier.core.plan.TestPlan;

/** The sole canonical location for outcome conversion and result aggregation. */
public final class Evaluator {
    private Evaluator() {}

    /** Canonical signature from docs/03-test-model.md section 7.5. */
    public static RunResult evaluate(
            CoverageCatalog catalog,
            TestPlan plan,
            List<ApplicabilityEvaluation> applicability,
            List<CaseRun> caseRuns,
            List<SuiteIncident> incidents) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(plan, "plan");
        applicability = List.copyOf(applicability == null ? List.of() : applicability);
        caseRuns = List.copyOf(caseRuns == null ? List.of() : caseRuns);
        incidents = List.copyOf(incidents == null ? List.of() : incidents);

        var selected = catalog.obligations().stream()
                .filter(obligation -> obligation.includedIn(plan.profile()))
                .toList();
        var selectedByKey = indexObligations(selected);
        var applicabilityByKey = indexApplicability(applicability, selectedByKey);
        requireConditionalApplicability(selected, applicabilityByKey);
        var casesByObligation = indexCases(caseRuns, selectedByKey);
        validateSuiteIncidents(caseRuns, incidents);

        var obligationResults = new ArrayList<ObligationResult>();
        for (var obligation : selected) {
            var evaluation = applicabilityByKey.get(obligation.key());
            var runs = casesByObligation.getOrDefault(obligation.key(), List.of());
            obligationResults.add(evaluateObligation(obligation, evaluation, runs));
        }

        var requirementResults = aggregateRequirements(obligationResults);
        var coverage = coverage(obligationResults, applicabilityByKey);
        var conformance = conformance(obligationResults, coverage.excludedByDeclaration());
        var completeness = completeness(obligationResults);

        return new RunResult(
                conformance,
                completeness,
                obligationResults,
                requirementResults,
                coverage,
                applicability,
                scopeQualifications(applicability),
                incidents);
    }

    public static Verdict toVerdict(Rfc2119Level level, CaseOutcome outcome) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(outcome, "outcome");
        return switch (outcome.outcome()) {
            case SATISFIED -> Verdict.PASS;
            case SATISFIED_WITH_NOTE -> Verdict.WARNING;
            case VIOLATED -> switch (level.levelClass()) {
                case MUST_CLASS -> Verdict.FAIL;
                case SHOULD_CLASS -> Verdict.WARNING;
                case MAY_CLASS -> Verdict.NOT_SUPPORTED;
            };
            case INDETERMINATE -> Verdict.INDETERMINATE;
            case INCONSISTENT -> Verdict.INCONSISTENT;
            case NOT_VERIFIED -> Verdict.NOT_VERIFIED;
        };
    }

    public static Verdict aggregate(Verdict left, Verdict right) {
        return Verdict.moreSevere(Objects.requireNonNull(left), Objects.requireNonNull(right));
    }

    private static ObligationResult evaluateObligation(
            Obligation obligation,
            ApplicabilityEvaluation applicability,
            List<CaseRun> caseRuns) {
        var effective = applicability == null
                ? (obligation.condition() == null ? EffectiveResult.TRUE : EffectiveResult.UNKNOWN)
                : applicability.effectiveResult();
        var conflict = applicability != null && applicability.conflict();
        var reasons = new ArrayList<String>();
        var caseIds = caseRuns.stream().map(CaseRun::id).toList();

        if (effective != EffectiveResult.TRUE && !caseRuns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cases must not execute before applicability is TRUE: " + obligation.key());
        }

        Verdict verdict;
        if (effective == EffectiveResult.FALSE) {
            verdict = Verdict.NOT_APPLICABLE;
        } else if (effective == EffectiveResult.UNKNOWN) {
            verdict = Verdict.NOT_VERIFIED;
            reasons.add("applicability_undetermined");
        } else if (obligation.testability() == Testability.NOT_OBSERVABLE) {
            if (!caseRuns.isEmpty()) {
                throw new IllegalArgumentException("NOT_OBSERVABLE obligation has case runs: " + obligation.key());
            }
            verdict = Verdict.NOT_OBSERVABLE;
        } else if (caseRuns.isEmpty()) {
            verdict = Verdict.NOT_VERIFIED;
            reasons.add("not_implemented");
        } else {
            verdict = null;
            for (var caseRun : caseRuns) {
                var caseVerdict = caseRun.outcome() == null
                        ? Verdict.ERROR
                        : toVerdict(obligation.level(), caseRun.outcome());
                verdict = verdict == null ? caseVerdict : aggregate(verdict, caseVerdict);
                if (caseRun.outcome() == null) {
                    reasons.add(caseRun.suiteErrorReason());
                } else if (caseRun.outcome().outcome() == Outcome.NOT_VERIFIED) {
                    reasons.add(caseRun.outcome().notVerifiedReason());
                } else if (caseRun.outcome().reasonCode() != null) {
                    reasons.add(caseRun.outcome().reasonCode());
                }
            }
        }

        if (conflict) {
            verdict = aggregate(verdict, Verdict.INCONSISTENT);
            reasons.add("applicability_conflict");
        }
        return new ObligationResult(
                obligation.key(), obligation.requirementId(), obligation.level(), verdict, caseIds, reasons);
    }

    private static List<RequirementResult> aggregateRequirements(List<ObligationResult> obligations) {
        var groups = new LinkedHashMap<String, List<ObligationResult>>();
        for (var obligation : obligations) {
            groups.computeIfAbsent(obligation.requirementId(), ignored -> new ArrayList<>()).add(obligation);
        }
        var results = new ArrayList<RequirementResult>();
        for (var entry : groups.entrySet()) {
            Verdict verdict = null;
            var keys = new ArrayList<String>();
            for (var obligation : entry.getValue()) {
                verdict = verdict == null ? obligation.verdict() : aggregate(verdict, obligation.verdict());
                keys.add(obligation.key());
            }
            results.add(new RequirementResult(entry.getKey(), verdict, keys));
        }
        return List.copyOf(results);
    }

    private static CoverageMetrics coverage(
            List<ObligationResult> obligations,
            Map<String, ApplicabilityEvaluation> applicability) {
        var counts = new EnumMap<Verdict, Integer>(Verdict.class);
        for (var verdict : Verdict.values()) counts.put(verdict, 0);
        for (var obligation : obligations) counts.merge(obligation.verdict(), 1, Integer::sum);

        var applicableMust = obligations.stream()
                .filter(result -> result.verdict() != Verdict.NOT_APPLICABLE)
                .filter(result -> result.level().levelClass() == LevelClass.MUST_CLASS)
                .toList();
        var mustNotObservable = count(applicableMust, result -> result.verdict() == Verdict.NOT_OBSERVABLE);
        var mustObservable = applicableMust.size() - mustNotObservable;
        var mustResolved = count(applicableMust, result -> switch (result.verdict()) {
            case PASS, WARNING, FAIL -> true;
            default -> false;
        });
        var mustUnresolved = count(applicableMust, result -> isUnresolved(result.verdict()));
        var exclusions = (int) applicability.values().stream()
                .filter(value -> value.basis() == Basis.DECLARATION_ONLY_EXCLUSION)
                .count();
        var ratio = mustObservable == 0 ? 1.0 : (double) mustResolved / mustObservable;
        return new CoverageMetrics(
                mustObservable,
                mustResolved,
                mustUnresolved,
                mustNotObservable,
                exclusions,
                ratio,
                counts);
    }

    private static Conformance conformance(List<ObligationResult> obligations, int declarationExclusions) {
        if (obligations.stream().anyMatch(result -> result.verdict() == Verdict.FAIL)) {
            return Conformance.NON_CONFORMANT;
        }
        var unresolvedMust = obligations.stream()
                .filter(result -> result.verdict() != Verdict.NOT_APPLICABLE)
                .filter(result -> result.verdict() != Verdict.NOT_OBSERVABLE)
                .filter(result -> result.level().levelClass() == LevelClass.MUST_CLASS)
                .anyMatch(result -> isUnresolved(result.verdict()));
        if (unresolvedMust) return Conformance.INDETERMINATE;
        if (declarationExclusions > 0) return Conformance.CONFORMANT_WITH_DECLARED_EXCLUSIONS;
        if (obligations.stream().anyMatch(result -> result.verdict() == Verdict.WARNING)) {
            return Conformance.CONFORMANT_WITH_WARNINGS;
        }
        return Conformance.CONFORMANT;
    }

    private static Completeness completeness(List<ObligationResult> obligations) {
        var unresolved = obligations.stream()
                .filter(result -> result.verdict() != Verdict.NOT_APPLICABLE)
                .filter(result -> result.verdict() != Verdict.NOT_OBSERVABLE)
                .anyMatch(result -> isUnresolved(result.verdict()));
        return unresolved ? Completeness.INCOMPLETE : Completeness.COMPLETE;
    }

    private static boolean isUnresolved(Verdict verdict) {
        return switch (verdict) {
            case NOT_VERIFIED, INDETERMINATE, INCONSISTENT, ERROR -> true;
            default -> false;
        };
    }

    private static int count(List<ObligationResult> values, Predicate<ObligationResult> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    private static Map<String, Obligation> indexObligations(List<Obligation> obligations) {
        var result = new LinkedHashMap<String, Obligation>();
        for (var obligation : obligations) result.put(obligation.key(), obligation);
        return result;
    }

    private static Map<String, ApplicabilityEvaluation> indexApplicability(
            List<ApplicabilityEvaluation> values,
            Map<String, Obligation> selected) {
        var result = new LinkedHashMap<String, ApplicabilityEvaluation>();
        for (var value : values) {
            var obligation = selected.get(value.obligationKey());
            if (obligation == null) {
                throw new IllegalArgumentException("Applicability is outside the selected profile: " + value.obligationKey());
            }
            if (obligation.condition() == null) {
                throw new IllegalArgumentException(
                        "Unconditional obligation must not receive applicability: " + value.obligationKey());
            }
            if (!obligation.condition().equals(value.predicate())) {
                throw new IllegalArgumentException(
                        "Applicability predicate does not match coverage: " + value.obligationKey());
            }
            if (result.put(value.obligationKey(), value) != null) {
                throw new IllegalArgumentException("Duplicate applicability: " + value.obligationKey());
            }
        }
        return result;
    }

    private static void requireConditionalApplicability(
            List<Obligation> selected,
            Map<String, ApplicabilityEvaluation> applicability) {
        for (var obligation : selected) {
            if (obligation.condition() != null && !applicability.containsKey(obligation.key())) {
                throw new IllegalArgumentException(
                        "Conditional obligation has no applicability evaluation: " + obligation.key());
            }
        }
    }

    private static Map<String, List<CaseRun>> indexCases(
            List<CaseRun> values,
            Map<String, Obligation> selected) {
        var result = new LinkedHashMap<String, List<CaseRun>>();
        var caseIds = new java.util.HashSet<String>();
        for (var value : values) {
            if (!caseIds.add(value.id())) throw new IllegalArgumentException("Duplicate case run: " + value.id());
            if (!selected.containsKey(value.obligationKey())) {
                throw new IllegalArgumentException("Case is outside the selected profile: " + value.obligationKey());
            }
            result.computeIfAbsent(value.obligationKey(), ignored -> new ArrayList<>()).add(value);
        }
        var immutable = new LinkedHashMap<String, List<CaseRun>>();
        for (var entry : result.entrySet()) immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        return immutable;
    }

    private static void validateSuiteIncidents(List<CaseRun> caseRuns, List<SuiteIncident> incidents) {
        var byId = new LinkedHashMap<String, CaseRun>();
        for (var caseRun : caseRuns) byId.put(caseRun.id(), caseRun);
        for (var incident : incidents) {
            if (!"UNKNOWN_DELIVERY".equals(incident.kind())) continue;
            var caseRun = byId.get(incident.caseId());
            if (caseRun != null && caseRun.outcome() != null
                    && caseRun.outcome().outcome() == Outcome.VIOLATED) {
                throw new IllegalArgumentException(
                        "UNKNOWN_DELIVERY must not become a target violation: " + incident.caseId());
            }
        }
    }

    private static List<ScopeQualification> scopeQualifications(
            List<ApplicabilityEvaluation> applicability) {
        record Group(ApplicabilityInput.ExclusionDeclaration exclusion, List<String> obligations) {}
        var groups = new LinkedHashMap<String, Group>();
        for (var evaluation : applicability) {
            if (evaluation.basis() != Basis.DECLARATION_ONLY_EXCLUSION) continue;
            var existing = groups.get(evaluation.predicate());
            if (existing == null) {
                var obligations = new ArrayList<String>();
                obligations.add(evaluation.obligationKey());
                groups.put(evaluation.predicate(), new Group(evaluation.exclusion(), obligations));
            } else {
                if (!existing.exclusion().equals(evaluation.exclusion())) {
                    throw new IllegalArgumentException(
                            "One exclusion predicate has inconsistent declarations: " + evaluation.predicate());
                }
                existing.obligations().add(evaluation.obligationKey());
            }
        }
        var result = new ArrayList<ScopeQualification>();
        for (var entry : groups.entrySet()) {
            var exclusion = entry.getValue().exclusion();
            result.add(new ScopeQualification(
                    "declared_exclusion", entry.getKey(), entry.getValue().obligations(), exclusion.reason(),
                    exclusion.attestedBy(), exclusion.attestedAt(), false));
        }
        return List.copyOf(result);
    }
}
