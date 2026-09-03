package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import com.samlscope.runner.access.RunAccessService;

class ManagementSessionRoutesTest {
    @Test
    void requiresTheExactOriginAndReturnsHardenedCookieWithoutEchoingTheAccessToken() throws Exception {
        var rawAccessToken = "a".repeat(43);
        var app = Javalin.create(config -> ManagementSessionRoutes.register(
                config, URI.create("https://app.example"), (runId, token) -> {
                    assertEquals(rawAccessToken, token);
                    return new RunAccessService.ManagementSession(runId, "s".repeat(43), "c".repeat(43));
                })).start(0);
        try {
            var endpoint = URI.create("http://127.0.0.1:" + app.port() + "/api/manage/session");
            var body = "{\"runId\":\"run_0123456789ABCDEFGHJKMNPQRS\",\"token\":\"" + rawAccessToken + "\"}";
            assertEquals(403, send(endpoint, body, null).statusCode());
            var response = send(endpoint, body, "https://app.example");
            assertEquals(200, response.statusCode(), response.body());
            var cookie = response.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(cookie.contains("Secure"));
            assertTrue(cookie.contains("HttpOnly"));
            assertTrue(cookie.contains("SameSite=Strict"));
            assertTrue(response.body().contains("csrfToken"));
            assertFalse(response.body().contains(rawAccessToken));
            assertEquals(400, send(URI.create(endpoint + "?token=" + rawAccessToken), body,
                    "https://app.example").statusCode());
        } finally {
            app.stop();
        }
    }

    private HttpResponse<String> send(URI endpoint, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) builder.header("Origin", origin);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
