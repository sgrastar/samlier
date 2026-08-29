package org.samlier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SamlierApplicationTest {
    @TempDir Path dataDirectory;

    @Test
    void createsPlanAndPublishesSignedMetadata() throws Exception {
        var config = new AppConfig(AppConfig.Mode.SELFHOSTED,
                URI.create("http://127.0.0.1:8080"), URI.create("http://127.0.0.1:8080"),
                dataDirectory, 8080, true, false, false);
        var app = SamlierApplication.create(config).start(0);
        try {
            var base = URI.create("http://127.0.0.1:" + app.port());
            var client = HttpClient.newHttpClient();
            var health = client.send(HttpRequest.newBuilder(base.resolve("/api/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"ok\""));
            assertTrue(health.headers().firstValue("Content-Security-Policy").orElseThrow()
                    .contains("form-action 'self'"));

            var requestBody = """
                    {
                      "name":"Example IdP",
                      "profile":"IDP_CORE",
                      "targetKind":"IDP",
                      "targetEntityId":"https://idp.example/entity",
                      "metadataSourceKind":"URL",
                      "metadataSourceLocation":"https://idp.example/metadata",
                      "suiteMetadataDelivery":"HTTP_URL",
                      "declaredFeatures":{},
                      "parameters":{"clockSkewToleranceSeconds":180,"metadataRefreshWaitSeconds":300,"testUserHint":""},
                      "interaction":{"allowBrowserSteps":true,"allowAttestation":true}
                    }
                    """;
            var created = client.send(HttpRequest.newBuilder(base.resolve("/api/plans"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode(), created.body());
            var planId = created.body().replaceFirst("(?s).*\"id\":\"(plan_[0-9A-Z]+)\".*", "$1");
            assertTrue(planId.matches("plan_[0-9A-HJKMNP-TV-Z]{26}"));

            var createdRun = client.send(HttpRequest.newBuilder(base.resolve("/api/plans/" + planId + "/runs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createdRun.statusCode(), createdRun.body());
            assertTrue(createdRun.body().contains("\"managementUrl\":null"));
            var runId = createdRun.body().replaceFirst("(?s).*\"id\":\"(run_[0-9A-Z]+)\".*", "$1");
            assertTrue(runId.matches("run_[0-9A-HJKMNP-TV-Z]{26}"));
            var reportShell = client.send(HttpRequest.newBuilder(base.resolve("/reports/" + runId)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reportShell.statusCode());
            assertTrue(reportShell.body().contains("<div id=\"root\"></div>"));

            var metadata = client.send(HttpRequest.newBuilder(base.resolve("/p/" + planId + "/metadata")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metadata.statusCode());
            assertEquals("application/samlmetadata+xml", metadata.headers()
                    .firstValue("Content-Type").orElseThrow());
            assertTrue(metadata.body().contains("<ds:Signature"));
            assertTrue(metadata.body().contains("<md:SPSSODescriptor"));
            assertTrue(metadata.body().contains("<md:IDPSSODescriptor"));

            var variant = client.send(HttpRequest.newBuilder(base.resolve(
                            "/p/" + planId + "/metadata?variant=no-key-info&run=" + runId)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, variant.statusCode(), variant.body());
            assertEquals("no-store", variant.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(variant.body().contains("mdv=no-key-info&amp;run=" + runId));
            var transcript = client.send(HttpRequest.newBuilder(base.resolve(
                            "/api/runs/" + runId + "/transcript")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, transcript.statusCode());
            assertTrue(transcript.body().contains("MetadataFetch"));
            assertTrue(transcript.body().contains("no-key-info"));
        } finally {
            app.stop();
        }
    }

    @Test
    void postPageUsesNonceAndEscapesUntrustedValues() {
        var page = HtmlPostPage.render(URI.create("https://sp.example/acs?x=1&y=2"),
                "<response>", "\"relay\"", "nonce-value");
        assertTrue(page.contains("nonce=\"nonce-value\""));
        assertTrue(page.contains("action=\"https://sp.example/acs?x=1&amp;y=2\""));
        assertTrue(page.contains("value=\"&lt;response&gt;\""));
        assertTrue(page.contains("value=\"&quot;relay&quot;\""));
        assertFalse(page.contains("<response>"));
    }
}
