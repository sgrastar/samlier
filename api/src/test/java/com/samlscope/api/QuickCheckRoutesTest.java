package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.runner.QuickCheckService;

class QuickCheckRoutesTest {
    @Test
    void delegatesToTheOperationalCheckAndSerializesItsDisclaimer() throws Exception {
        var app = Javalin.create(config -> QuickCheckRoutes.register(config, runId ->
                new QuickCheckService.QuickCheckResult(
                        runId, QuickCheckService.DISCLAIMER, List.of()))).start(0);
        try {
            var runId = "run_0123456789ABCDEFGHJKMNPQRS";
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + app.port() + "/api/runs/" + runId + "/quick-check"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains(runId));
            assertTrue(response.body().contains("operational check, not a conformance determination"));
        } finally {
            app.stop();
        }
    }
}
