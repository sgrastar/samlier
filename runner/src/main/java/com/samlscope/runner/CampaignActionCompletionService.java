package com.samlscope.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.samlscope.runner.cases.SharedBrowserPolicyTestCase;

/** Completes one server-defined shared operator action without accepting an outcome or verdict. */
public final class CampaignActionCompletionService {
    private final RunCampaignQuery campaigns;
    private final TestCaseRegistry registry;
    private final BrowserCompletionExecutor browser;

    public CampaignActionCompletionService(
            RunCampaignQuery campaigns,
            TestCaseRegistry registry,
            BrowserCompletionExecutor browser) {
        this.campaigns = Objects.requireNonNull(campaigns, "campaigns");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public Result complete(String runId, String campaignId, String actionId) {
        var report = campaigns.report(required(runId, "runId"));
        var campaign = report.campaigns().stream()
                .filter(value -> value.id().equals(required(campaignId, "campaignId")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown evidence campaign"));
        var action = campaign.actions().stream()
                .filter(value -> value.id().equals(required(actionId, "actionId")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown campaign action"));
        if (campaign.actionKind() != RunCampaignQuery.ActionKind.CONFIGURATION) {
            throw new IllegalArgumentException("Only shared configuration actions can be completed here");
        }
        var pending = action.remainingCaseIds();
        for (var caseId : pending) {
            if (!(registry.require(caseId) instanceof SharedBrowserPolicyTestCase)) {
                throw new IllegalStateException("Campaign action contains a non-shareable browser case: " + caseId);
            }
        }
        var completed = new ArrayList<BrowserCompletionExecutor.Result>();
        for (var caseId : pending) completed.add(browser.complete(runId, caseId));
        return new Result(runId, campaign.id(), action.id(), List.copyOf(completed));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public record Result(
            String runId,
            String campaignId,
            String actionId,
            List<BrowserCompletionExecutor.Result> completed) {
        public Result { completed = List.copyOf(completed); }
    }
}
