package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.runner.RunCampaignQuery;

class CampaignRoutesTest {
    @Test
    void returnsCasesAndActionsAsSeparateQuantities() throws Exception {
        var report = new RunCampaignQuery.CampaignReport(
                "run", 220,
                Map.of(
                        RunCampaignQuery.EvidenceClass.PROTOCOL_OBSERVED, 143,
                        RunCampaignQuery.EvidenceClass.OPERATOR_ASSISTED, 55,
                        RunCampaignQuery.EvidenceClass.SELF_ATTESTED, 22),
                List.of(new RunCampaignQuery.PlanSummary(
                        RunCampaignQuery.Plan.QUICK, 143, 12, 3, 5, 0, 0, 0,
                        10, 20, 15, true)),
                List.of(), List.of(), 120, 4, 96);
        var app = Javalin.create(config -> CampaignRoutes.register(config, ignored -> report)).start(0);
        try {
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + app.port() + "/api/runs/run/campaigns"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"cases\":220"), response.body());
            assertTrue(response.body().contains("\"deliberateUserActions\":12"), response.body());
            assertTrue(response.body().contains("\"externallyVerifiedCases\":120"), response.body());
            assertTrue(response.body().contains("\"selfAttestedCases\":4"), response.body());
        } finally {
            app.stop();
        }
    }
}
