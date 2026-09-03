package com.samlscope.runner;

import java.util.List;
import java.util.Map;

/** Read-only projection that separates conformance cases from deliberate operator actions. */
@FunctionalInterface
public interface RunCampaignQuery {
    CampaignReport report(String runId);

    record CampaignReport(
            String runId,
            int cases,
            Map<EvidenceClass, Integer> casesByEvidenceClass,
            List<PlanSummary> plans,
            List<Campaign> campaigns,
            List<CaseClassification> classifications,
            int externallyVerifiedCases,
            int selfAttestedCases,
            int notVerifiedCases) {
        public CampaignReport {
            casesByEvidenceClass = Map.copyOf(casesByEvidenceClass);
            plans = List.copyOf(plans);
            campaigns = List.copyOf(campaigns);
            classifications = List.copyOf(classifications);
        }
    }

    record CaseClassification(
            String caseId,
            Plan plan,
            EvidenceClass evidenceClass,
            String campaignId,
            ActionKind actionKind,
            boolean freshSessionRequired,
            boolean resolved,
            String outcome,
            List<String> expectedTranscriptEvidence) {
        public CaseClassification {
            expectedTranscriptEvidence = List.copyOf(expectedTranscriptEvidence);
        }
    }

    record PlanSummary(
            Plan plan,
            int cases,
            int deliberateUserActions,
            int remainingUserActions,
            int loginActions,
            int configurationActions,
            int metadataRefreshActions,
            int selfAttestationSections,
            int estimatedMinutesMin,
            int estimatedMinutesMax,
            int actionBudget,
            boolean budgetMet) {}

    record Campaign(
            String id,
            String title,
            Plan plan,
            EvidenceClass evidenceClass,
            ActionKind actionKind,
            int deliberateUserActions,
            int remainingUserActions,
            boolean freshSessionRequired,
            List<String> caseIds,
            List<String> remainingCaseIds,
            List<String> expectedTranscriptEvidence,
            List<CampaignAction> actions) {
        public Campaign {
            caseIds = List.copyOf(caseIds);
            remainingCaseIds = List.copyOf(remainingCaseIds);
            expectedTranscriptEvidence = List.copyOf(expectedTranscriptEvidence);
            actions = List.copyOf(actions);
        }
    }

    record CampaignAction(
            String id,
            List<String> caseIds,
            List<String> remainingCaseIds) {
        public CampaignAction {
            caseIds = List.copyOf(caseIds);
            remainingCaseIds = List.copyOf(remainingCaseIds);
        }
    }

    enum Plan { QUICK, STANDARD, FULL }
    enum EvidenceClass { PROTOCOL_OBSERVED, OPERATOR_ASSISTED, SELF_ATTESTED }
    enum ActionKind { NONE, LOGIN, CONTINUE, CONFIGURATION, METADATA_REFRESH, SELF_CHECK }
}
