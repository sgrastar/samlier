package org.samlier.runner;

import org.samlier.core.caseexec.CaseState;

/** Marker and presentation contract for a persisted scenario delivered through a real browser. */
public interface BrowserFrontChannelScenario extends EvidenceCampaignCase {
    default boolean requiresFreshSession(CaseState state) { return false; }

    @Override default String evidenceCampaignId() { return "active-probe-chain"; }

    @Override default String evidenceCampaignTitle() { return "Browser-assisted SAML protocol scenarios"; }

    @Override default RunCampaignQuery.ActionKind evidenceActionKind() {
        return RunCampaignQuery.ActionKind.LOGIN;
    }

    /** Planned human checkpoints inside the automatically chained scenario. */
    default int plannedDeliberateActions() { return 0; }

    String instructionsEn(CaseState state);
}
