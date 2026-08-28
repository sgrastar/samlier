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

            var metadata = client.send(HttpRequest.newBuilder(base.resolve("/p/" + planId + "/metadata")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metadata.statusCode());
            assertEquals("application/samlmetadata+xml", metadata.headers()
                    .firstValue("Content-Type").orElseThrow());
            assertTrue(metadata.body().contains("<ds:Signature"));
            assertTrue(metadata.body().contains("<md:SPSSODescriptor"));
            assertTrue(metadata.body().contains("<md:IDPSSODescriptor"));
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
