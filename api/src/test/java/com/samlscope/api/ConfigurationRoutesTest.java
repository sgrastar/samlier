package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.evaluation.CaseOutcome;
import com.samlscope.runner.ConfigurationExecutor;

class ConfigurationRoutesTest {
    @Test
    void acceptsOnlyTheSharedAnswerAndRejectsClientOutcomeInjection() throws Exception {
        var invocation = new AtomicReference<List<String>>();
        ConfigurationExecutor executor = (runId, caseId, value, note) -> {
            invocation.set(List.of(runId, caseId, value, note));
            return new ConfigurationExecutor.Result(
                    runId, caseId, CaseExecutionStatus.FINISHED,
                    CaseOutcome.notVerified("capability_undetermined", "configuration.capability-undetermined"));
        };
        var app = Javalin.create(config -> ConfigurationRoutes.register(config, executor)).start(0);
        try {
            var runId = "run_0123456789ABCDEFGHJKMNPQRS";
            var caseId = "IIP-MD01-a-idp-01";
            var uri = URI.create(
                    "http://127.0.0.1:" + app.port() + "/api/runs/" + runId
                            + "/cases/" + caseId + "/configure");
            var injected = post(uri,
                    "{\"value\":\"confirmed\",\"note\":\"\",\"outcome\":\"SATISFIED\"}");
            var rejected = HttpClient.newHttpClient().send(injected, HttpResponse.BodyHandlers.ofString());
            assertTrue(rejected.statusCode() >= 400, rejected.body());
            assertEquals(null, invocation.get());

            var response = HttpClient.newHttpClient().send(
                    post(uri, "{\"value\":\"capability_undetermined\",\"note\":\"Unknown\"}"),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(List.of(runId, caseId, "capability_undetermined", "Unknown"), invocation.get());
            assertTrue(response.body().contains("NOT_VERIFIED"), response.body());
        } finally {
            app.stop();
        }
    }

    private HttpRequest post(URI uri, String body) {
        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
