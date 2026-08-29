package org.samlier.core.evaluation;

import java.util.List;
import java.util.Map;
import java.time.Instant;

public record RunResult(
        Conformance conformance,
        Completeness completeness,
        List<ObligationResult> obligations,
        List<RequirementResult> requirements,
        CoverageMetrics coverage,
        List<ApplicabilityEvaluation> applicability,
        List<ScopeQualification> scopeQualifications,
        List<SuiteIncident> suiteIncidents) {

    public RunResult {
        obligations = List.copyOf(obligations);
        requirements = List.copyOf(requirements);
        applicability = List.copyOf(applicability);
        scopeQualifications = List.copyOf(scopeQualifications);
        suiteIncidents = List.copyOf(suiteIncidents);
    }

    public enum Conformance {
        CONFORMANT,
        CONFORMANT_WITH_WARNINGS,
        CONFORMANT_WITH_DECLARED_EXCLUSIONS,
        NON_CONFORMANT,
        INDETERMINATE
    }

    public enum Completeness { COMPLETE, INCOMPLETE }

    public record ObligationResult(
            String key,
            String requirementId,
            Rfc2119Level level,
            Verdict verdict,
            List<String> caseIds,
            List<String> reasons) {
        public ObligationResult {
            caseIds = List.copyOf(caseIds);
            reasons = List.copyOf(reasons);
        }
    }

    public record RequirementResult(String id, Verdict verdict, List<String> obligationKeys) {
        public RequirementResult {
            obligationKeys = List.copyOf(obligationKeys);
        }
    }

    public record ScopeQualification(
            String kind,
            String predicate,
            List<String> excludedObligations,
            String reason,
            String attestedBy,
            Instant attestedAt,
            boolean verified) {
        public ScopeQualification {
            excludedObligations = List.copyOf(excludedObligations);
            if (!"declared_exclusion".equals(kind)) {
                throw new IllegalArgumentException("Unsupported scope qualification kind");
            }
            if (verified) throw new IllegalArgumentException("Declared exclusions are not verified");
        }
    }

    public record CoverageMetrics(
            int mustObservable,
            int mustResolved,
            int mustUnresolved,
            int mustNotObservable,
            int excludedByDeclaration,
            double verifiedRatio,
            Map<Verdict, Integer> verdictCounts) {
        public CoverageMetrics {
            verdictCounts = Map.copyOf(verdictCounts);
        }
    }
}
