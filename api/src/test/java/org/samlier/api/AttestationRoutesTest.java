package org.samlier.api;

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
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.runner.AttestationExecutor;

class AttestationRoutesTest {
    @Test
    void acceptsOnlyAnOptionValueAndDelegatesOutcomeSelectionToTheServer() throws Exception {
        var invocation = new AtomicReference<List<String>>();
        AttestationExecutor executor = (runId, caseId, value, note) -> {
            invocation.set(List.of(runId, caseId, value, note));
            return new AttestationExecutor.Result(
                    runId, caseId, CaseExecutionStatus.FINISHED,
                    CaseOutcome.of(Outcome.VIOLATED, "attestation.accepted", List.of()));
        };
        var app = Javalin.create(config -> AttestationRoutes.register(config, executor)).start(0);
        try {
            var runId = "run_0123456789ABCDEFGHJKMNPQRS";
            var caseId = "IIP-G02-c-idp-01";
            var uri = URI.create(
                    "http://127.0.0.1:" + app.port() + "/api/runs/" + runId
                            + "/cases/" + caseId + "/attest");
            var injected = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"value\":\"accepted\",\"note\":\"Observed login\","
                                    + "\"outcome\":\"SATISFIED\",\"verdict\":\"PASS\"}"))
                    .build();

            var rejected = HttpClient.newHttpClient().send(injected, HttpResponse.BodyHandlers.ofString());
            assertTrue(rejected.statusCode() >= 400, rejected.body());
            assertEquals(null, invocation.get());

            var request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"value\":\"accepted\",\"note\":\"Observed login\"}"))
                    .build();

            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response.body());
            assertEquals(List.of(runId, caseId, "accepted", "Observed login"), invocation.get());
            assertTrue(response.body().contains("VIOLATED"), response.body());
            assertFalseResultDoesNotEchoClientVerdict(response.body());
        } finally {
            app.stop();
        }
    }

    private void assertFalseResultDoesNotEchoClientVerdict(String body) {
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("PASS"), body);
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("SATISFIED"), body);
    }
}
