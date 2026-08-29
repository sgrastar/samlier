package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.runner.InteractionQuery;

class InteractionRoutesTest {
    @Test
    void returnsTheSafeInteractionProjectionWithoutCaseState() throws Exception {
        var app = Javalin.create(config -> InteractionRoutes.register(config, runId -> List.of(
                new InteractionQuery.PendingInteraction(
                        "IIP-G02-c-idp-01", InteractionQuery.Kind.ATTESTATION, "attestation.g02",
                        "Confirm that the approved evidence was observed.", null,
                        Instant.parse("2026-08-29T04:00:00Z"), List.of("preserved", "truncated"))))).start(0);
        try {
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + app.port() + "/api/runs/run/interactions"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("preserved"), response.body());
            assertTrue(response.body().contains("attestation.g02"), response.body());
            assertTrue(response.body().contains("approved evidence"), response.body());
            assertTrue(response.body().contains("\"expiresAt\":\"2026-08-29T04:00:00Z\""), response.body());
            assertFalse(response.body().contains("\"expiresAt\":178"), response.body());
            assertFalse(response.body().contains("CaseState"), response.body());
            assertFalse(response.body().contains("secret"), response.body());
        } finally {
            app.stop();
        }
    }
}
