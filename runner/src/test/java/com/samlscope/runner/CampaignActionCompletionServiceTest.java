package com.samlscope.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.RunCampaignQuery.ActionKind;
import com.samlscope.runner.RunCampaignQuery.Campaign;
import com.samlscope.runner.RunCampaignQuery.CampaignAction;
import com.samlscope.runner.RunCampaignQuery.CampaignReport;
import com.samlscope.runner.RunCampaignQuery.EvidenceClass;
import com.samlscope.runner.RunCampaignQuery.Plan;
import com.samlscope.runner.cases.BrowserPrompt;
import com.samlscope.runner.cases.SharedBrowserPolicyTestCase;

class CampaignActionCompletionServiceTest {
    @Test
    void completesEveryServerDefinedCaseInOneSharedAction() {
        var completed = new ArrayList<String>();
        var browser = browser(completed);
        var first = new SharedBrowserPolicyTestCase(delegate("IIP-ALG04-a-idp-01"));
        var second = new SharedBrowserPolicyTestCase(delegate("IIP-ALG04-b-idp-01"));
        var service = new CampaignActionCompletionService(
                ignored -> report(List.of(first.id(), second.id())),
                new TestCaseRegistry(List.of(first, second)), browser);

        var result = service.complete("run", "campaign", "content-encryption-policy");

        assertEquals(List.of(first.id(), second.id()), completed);
        assertEquals(2, result.completed().size());
    }

    @Test
    void validatesTheWholeActionBeforeCompletingAnyCase() {
        var completed = new ArrayList<String>();
        var shared = new SharedBrowserPolicyTestCase(delegate("IIP-ALG04-a-idp-01"));
        var unshared = delegate("IIP-ALG04-b-idp-01");
        var service = new CampaignActionCompletionService(
                ignored -> report(List.of(shared.id(), unshared.id())),
                new TestCaseRegistry(List.of(shared, unshared)), browser(completed));

        assertThrows(IllegalStateException.class,
                () -> service.complete("run", "campaign", "content-encryption-policy"));
        assertEquals(List.of(), completed);
    }

    private BrowserCompletionExecutor browser(List<String> completed) {
        return (runId, caseId) -> {
            completed.add(caseId);
            return new BrowserCompletionExecutor.Result(runId, caseId,
                    com.samlscope.core.caseexec.CaseExecutionStatus.FINISHED, null);
        };
    }

    private CampaignReport report(List<String> cases) {
        var action = new CampaignAction("content-encryption-policy", cases, cases);
        var campaign = new Campaign(
                "campaign", "Crypto", Plan.STANDARD, EvidenceClass.OPERATOR_ASSISTED,
                ActionKind.CONFIGURATION, 1, 1, false, cases, cases, List.of(), List.of(action));
        return new CampaignReport(
                "run", cases.size(), Map.of(EvidenceClass.OPERATOR_ASSISTED, cases.size()),
                List.of(), List.of(campaign), List.of(), 0, 0, cases.size());
    }

    private TestCase delegate(String id) {
        return new BrowserDelegate(id);
    }

    private record BrowserDelegate(String id) implements TestCase, BrowserPrompt {
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String browserInstructionsEn() { return "Apply policy."; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new UnsupportedOperationException();
        }
    }
}
