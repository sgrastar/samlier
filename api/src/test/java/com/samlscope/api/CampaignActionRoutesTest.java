package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.BrowserCompletionExecutor;
import com.samlscope.runner.CampaignActionCompletionService;
import com.samlscope.runner.RunCampaignQuery;
import com.samlscope.runner.TestCaseRegistry;
import com.samlscope.runner.cases.BrowserPrompt;
import com.samlscope.runner.cases.SharedBrowserPolicyTestCase;

class CampaignActionRoutesTest {
    @Test
    void completesOnlyTheServerDefinedActionAndRejectsOutcomeInjection() throws Exception {
        var completed = new ArrayList<String>();
        var first = new SharedBrowserPolicyTestCase(delegate("IIP-ALG04-a-idp-01"));
        var second = new SharedBrowserPolicyTestCase(delegate("IIP-ALG04-b-idp-01"));
        BrowserCompletionExecutor browser = (runId, caseId) -> {
            completed.add(caseId);
            return new BrowserCompletionExecutor.Result(runId, caseId, CaseExecutionStatus.FINISHED, null);
        };
        var cases = List.of(first.id(), second.id());
        var action = new RunCampaignQuery.CampaignAction("content-encryption-policy", cases, cases);
        var campaign = new RunCampaignQuery.Campaign(
                "campaign", "Crypto", RunCampaignQuery.Plan.STANDARD,
                RunCampaignQuery.EvidenceClass.OPERATOR_ASSISTED,
                RunCampaignQuery.ActionKind.CONFIGURATION, 1, 1, false,
                cases, cases, List.of(), List.of(action));
        var report = new RunCampaignQuery.CampaignReport(
                "run", 2, Map.of(RunCampaignQuery.EvidenceClass.OPERATOR_ASSISTED, 2),
                List.of(), List.of(campaign), List.of(), 0, 0, 2);
        var service = new CampaignActionCompletionService(
                ignored -> report, new TestCaseRegistry(List.of(first, second)), browser);
        var app = Javalin.create(config -> CampaignActionRoutes.register(config, service)).start(0);
        try {
            var uri = URI.create("http://127.0.0.1:" + app.port()
                    + "/api/runs/run/campaigns/campaign/actions/content-encryption-policy/complete");
            var injected = post(uri, "{\"outcome\":\"SATISFIED\"}");
            var rejected = HttpClient.newHttpClient().send(
                    injected, HttpResponse.BodyHandlers.ofString());
            assertTrue(rejected.statusCode() >= 400, rejected.body());
            assertEquals(List.of(), completed);

            var response = HttpClient.newHttpClient().send(
                    post(uri, "{}"), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(cases, completed);
            assertTrue(response.body().contains("content-encryption-policy"), response.body());
            assertTrue(!response.body().contains("SATISFIED"), response.body());
        } finally {
            app.stop();
        }
    }

    private HttpRequest post(URI uri, String body) {
        return HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private TestCase delegate(String id) { return new BrowserDelegate(id); }

    private record BrowserDelegate(String id) implements TestCase, BrowserPrompt {
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String browserInstructionsEn() { return "Apply policy."; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new UnsupportedOperationException();
        }
    }
}
